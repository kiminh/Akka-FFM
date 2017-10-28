//http://bytes.codes/2013/01/17/Distributing_Akka_Workloads_And_Shutting_Down_After/
//http://stackoverflow.com/questions/22383939/scala-read-file-process-lines-and-write-output-to-a-new-file-using-concurrent
//http://doc.akka.io/docs/akka/2.4/scala/stream/stream-quickstart.html
//https://ivanyu.me/blog/2016/12/12/about-akka-streams/#more-983
//https://tech.smartling.com/crawling-the-web-with-akka-streams-60ed8b3485e4#.18grzjuho
//https://github.com/akka/akka/issues/20031
//http://liferepo.blogspot.se/2015/01/creating-reactive-streams-components-on.html
//https://github.com/CogniStreamer/serilog-sinks-akkaactor
//https://ivanyu.me/docs/alpakka/latest/file.html/blog/2016/12/12/about-akka-streams/#more-983
//http://doc.akka.io/docs/akka/current/scala/stream/stream-integrations.html#Sink_actorRefWithAck
//http://blog.lancearlaus.com/akka/streams/scala/2015/05/27/Akka-Streams-Balancing-Buffer/

import akka.actor.{ActorLogging, ActorRef, Props, _}
import akka.routing.RoundRobinPool
import akka.stream.OverflowStrategy
import akka.stream.actor.{ActorSubscriber, OneByOneRequestStrategy, RequestStrategy}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.routing.Broadcast
import akka.actor.PoisonPill
import akka.pattern.gracefulStop
// Master messages that say what happens.
object Master {

  case object Done

  case object Start

  case object JobDone

  case object Init

  case object Ack

  case object Complete

  case object Failure

  case class ShardRequest(x:Int)

  case object InitParameters

  case object WorkersFinished
}

class Master(numberOfShards:Int,
             activation: Types.ActivationFunction,
             activationDerivative: Types.ActivationFunction
            ) extends ActorSubscriber with ActorLogging {
  override protected def requestStrategy: RequestStrategy = OneByOneRequestStrategy
  protected def overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure

  val actorName = "Master"
  implicit val timeout = Timeout(15.second)
  val save_gradient:Boolean = true
  val output_gradient:String = "output_gradient.txt"
  val save_weights:Boolean = true
  val output_weights:String = "output_weights.txt"

  val k:Int = 2
  val l:Int = 16 //396486
  val parametersActor = context.actorOf(Props(
    new Parameters(eta = Types.toMtype(0.1)
      , lambda = Types.toMtype(0.00002)
      , l = l  // number of lines in a file
      , n = 98 // number of different values
      , m = 4  // number of fields
      , k = k
      , weightsInitDist = breeze.stats.distributions.Uniform(0, 1)
      //weightsInitDist = breeze.stats.distributions.Gaussian(0, 1)
      , epochs = 1
    ))
  )

  log.info(s"Parameters actor initiated.")
  val dataShards = (1 to numberOfShards).toSeq

  val lossTrackerActor: ActorRef = context.actorOf(Props(new Loss(l, self)))

  val dataShardActors: ActorRef =
    context.actorOf(RoundRobinPool(numberOfShards).props(Props(new DataShard(
        activation = activation
      , activationDerivative = activationDerivative
      , parametersActor = parametersActor
      , k = k
      , lossTrackerActor = lossTrackerActor
    ))), "router2")

  log.info(s"${dataShards.size} data shards initiated!")

  var numShardsFinished:Int = 0
  var fileSource:ActorRef = _
  var readingComplete:Boolean = false
  var counter:Int = 0
  override def receive: Receive = {

    case Master.InitParameters => {

      /*Those msg are blocking, because we do not want to start processing file
        without having the parameters initialized, or loaded if we want to restore them from file.*/

      val future1: Future[String] = ask(parametersActor, Parameters.Init).mapTo[String]
      Await.result(future1, 50 seconds)

      if (scala.reflect.io.File(scala.reflect.io.Path(output_weights)).exists) {
        val future3: Future[String] = ask(parametersActor, Parameters.LoadWeights(output_weights)).mapTo[String]
        Await.result(future3, 50 seconds)
      }

      if (scala.reflect.io.File(scala.reflect.io.Path(output_gradient)).exists) {
        val future5: Future[String] = ask(parametersActor, Parameters.LoadGradients(output_gradient)).mapTo[String]
        Await.result(future5, 50 seconds)
      }

      self ! Master.Start
    }
    case Master.Start => {
      log.info("Starting reading the file")
      fileSource ! Master.Ack //start reading file
      for(i <- 1 until numberOfShards)  dataShardActors ! DataShard.ReadyToProcess
    }

    case Master.Init => {log.info(" == Master.Init == ")
                         fileSource  = sender
    }

    case Master.Complete => log.info(" == Master.Complete == ")

    case Master.WorkersFinished => {
      log.info(" == Master.WorkersFinished == ")
      if (save_weights) {
        val future5: Future[String] = ask(parametersActor, Parameters.WriteWeights(output_weights)).mapTo[String]
        Await.result(future5, 50 seconds)
      }

      if (save_gradient) {
        val future5: Future[String] = ask(parametersActor, Parameters.WriteGradient(output_weights)).mapTo[String]
        Await.result(future5, 50 seconds)
      }

      log.info(" == Stopping all actors == ")
      val stopped: Future[Boolean] = gracefulStop(context.parent, 2 second)
      Await.result(stopped, 3 second)
    }
    case Master.Failure => log.info(" == Master.Failure == ")
    case Status.Failure => log.info(" == Status.Failure ==")
    case dataPoint: FFMRow => {
      /* Forward msg so dataShard can reply to the original sender
         when it finished processing dataPoint, that is, send
         ack msg. so backpressure will be in place if needed.
         */
      dataShardActors forward dataPoint
    }
  }
}

import java.nio.file.Paths
import breeze.linalg.{DenseVector => BDV, Vector => BV}
import akka.actor.{ActorSystem, Props, Status}
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.javadsl.Framing
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import breeze.numerics.sigmoid
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

object AkkaFFM extends App {

  val customConf = ConfigFactory.parseString("""
   akka {
   # JVM shutdown, System.exit(-1), in case of a fatal error,
   # such as OutOfMemoryError
   jvm-exit-on-fatal-error = on
   # Toggles whether threads created by this ActorSystem should be daemons or not
   daemonic = off
   actor {
     actor.provider = "akka.remote.RemoteActorRefProvider"
     typed {
       # Default timeout for typed actor methods with non-void return type
       timeout = 5s
     }
     creation-timeout = 90s
   }
   remote {
     enabled-transports = ["akka.remote.netty.tcp"]
     netty {
       hostname = ""
       port = 2552
       # (O) Sets the connectTimeoutMillis of all outbound connections,
       # i.e. how long a connect may take until it is timed out
       connection-timeout = 120s
     }
     server-socket-worker-pool {
         # Min number of threads to cap factor-based number to
         pool-size-min = 2

         # The pool size factor is used to determine thread pool size
         # using the following formula: ceil(available processors * factor).
         # Resulting size is then bounded by the pool-size-min and
         # pool-size-max values.
         pool-size-factor = 1.0

         # Max number of threads to cap factor-based number to
         pool-size-max = 8
       }
     untrusted-mode = off
   }
   enable-additional-serialization-bindings = on
   serializers {
       proto = "akka.remote.serialization.ProtobufSerializer"
       daemon-create = "akka.remote.serialization.DaemonMsgCreateSerializer"
   }
   serialization-bindings {
       "java.io.Serializable" = none
       "com.google.protobuf.GeneratedMessage" = proto
       "akka.remote.DaemonMsgCreate" = daemon-create
     }
 }
          """)
  // ConfigFactory.load sandwiches customConfig between default reference
  // config and default overrides, and then resolves it.
  implicit val system = ActorSystem("HelloSystem", ConfigFactory.load(customConf))

  val ModelMaster = system.actorOf(Props(new Master(
      numberOfShards = 8
      , activation = (x: BDV[Types.mtype]) => x.map(el => sigmoid(el))
      , activationDerivative = (x: BDV[Types.mtype]) => x.map(el => sigmoid(el) * (1 - sigmoid(el)))
      )
    )
  )

  implicit val materializer = ActorMaterializer()
  def lineToFFMRow(line:String) :FFMRow ={
    val splitted = line.split(" ")
    val y = Types.toMtype(splitted(0)) // 1.0 else -1.0
    val FFMNodeVec:BV[FFMNode] = new BDV(splitted.slice(1,splitted.size).map(x=>{
      val data = x.split(":")
      FFMNode(data(0).toInt, data(1).toInt, Types.toMtype(data(2)))
    }).toArray)

    FFMRow(y, FFMNodeVec)
  }

  val file_name:String ="/home/marcgrab/Downloads/libffm_toy/tr.ffm"
  // "/media/small_ssd/libffm-local_train/part-00000"
  val file_source:Source[FFMRow, Future[IOResult]] = FileIO.fromPath(Paths.get(file_name)) //fromPath since 2.4.5
    //.via(Compression.gunzip())
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
    .map(_.utf8String)
    .map(x => lineToFFMRow(x))

  file_source.to(Sink.actorRefWithAck(ModelMaster
    ,onInitMessage=Master.Init
    ,ackMessage=Master.Ack
    ,onCompleteMessage=Master.Complete
    ,onFailureMessage=Status.Failure
    //,onFailureMessage:(Throwable)
  )//.withAttributes(inputBuffer(100, 100))
  ).run()


  val terminator = system.actorOf(Props(new Terminator(ModelMaster)), "app-terminator")

} //FMMAkkaScala
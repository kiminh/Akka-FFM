import akka.actor.{Actor, ActorLogging, ActorRef, _}
import akka.pattern.ask
import akka.util.Timeout
import breeze.linalg.{SliceVector, sum}
import breeze.numerics.{exp, sqrt, log => ln}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

object DataShard {

  case object ReadyToProcess

  case object FetchParameters

}

class DataShard(  activation: Types.ActivationFunction
                  , activationDerivative: Types.ActivationFunction
                  , parametersActor: ActorRef
                  , k:Int
                  , lossTrackerActor:ActorRef
               ) extends Actor with ActorLogging {
  implicit val timeout = Timeout(15.second)

  def receive = {
    case dataPoint:FFMRow => {
      parametersActor ! Parameters.RequestParameters
      val r:Types.mtype = Types.toMtype(1.0)/sum(dataPoint.features.map((node:FFMNode) => node.v*node.v)) //r is ok!
      val fj:List[(Int,Int)] = fj_for_ffrow(dataPoint)
      val future_weights = ask(parametersActor, Parameters.RequestWeights(fj)).mapTo[Array[SliceVector[(Int,Int), Types.mtype]]]
      val current_weights = Await.result(future_weights, timeout.duration) //.asInstanceOf[SliceVector[(Int,Int), Double]]
      val t:Types.mtype = wTx(fj, dataPoint, current_weights, r=r, k=k, do_update = false)
      val y:Types.mtype = dataPoint.label
      val expnyt:Types.mtype = Types.toMtype(exp(-y * t))
      val local_loss:Types.mtype = Types.toMtype(ln(1.0+expnyt))
      val kappa:Types.mtype = Types.toMtype(-y * expnyt / (1.0+expnyt))

      val future_gradient = ask(parametersActor, Parameters.RequestGradient(fj)).mapTo[Array[SliceVector[(Int,Int), Types.mtype]]]
      val current_gradient = Await.result(future_gradient, timeout.duration) //.asInstanceOf[SliceVector[(Int,Int), Double]]


      wTx(fj, dataPoint, current_weights, current_gradient, kappa = kappa, k=k, do_update = true)

      sender ! Master.Ack

      /* just making sure that the counter has been updated, blocking */
      lossTrackerActor ! Loss.UpdateLoss(local_loss)
      //val current_loss = Await.result(future_loss, timeout.duration).asInstanceOf[Boolean]
    }
  }

  def fj_for_ffrow(row:FFMRow): List[(Int,Int)] = {
    val indexes = new ListBuffer[(Int,Int)]
    val i_max = row.features.size
    var i = 0
    while(i < i_max) {
      var j = i + 1
      val node1: FFMNode = row.features(i)
      while (j < i_max) {
        val node2: FFMNode = row.features(j)
        indexes += Tuple2(node2.f, node1.j)
        indexes += Tuple2(node1.f, node2.j)
        j += 1
      }
      i += 1
    }
    indexes.toList
  }


  def wTx(fj:List[(Int,Int)], row:FFMRow, w:Array[SliceVector[(Int,Int),Types.mtype]], wg:Array[SliceVector[(Int,Int), Types.mtype]] = null, k:Int, r:Types.mtype = Types.toMtype(1.0), kappa:Types.mtype = Types.toMtype(0.0), eta:Types.mtype =Types.toMtype(0.1), lambda:Types.mtype = Types.toMtype(0.00002), do_update:Boolean = false): Types.mtype = {
    var t:Types.mtype = Types.toMtype(0.0)
    var w_ind:Int =0
    val i_max = row.features.size
    var i: Int = 0
    while (i < i_max) {
      var j = i + 1
      while (j < i_max) {
        val node1: FFMNode = row.features(i)
        val node2: FFMNode = row.features(j)
        val v = node1.v * node2.v * r

        if(do_update) {
          var d = 0
          while(d < k ) {
            val w1: Types.mtype = w(d)(w_ind)
            val w2: Types.mtype = w(d)(w_ind + 1)

            val g1 = lambda * w1 + kappa * v * w2
            val g2 = lambda * w2 + kappa * v * w1

            wg(d)(w_ind) = wg(d)(w_ind)+ g1 * g1
            wg(d)(w_ind + 1) = wg(d)(w_ind + 1) + g2 * g2

            w(d)(w_ind) = w(d)(w_ind) - eta * g1 / sqrt(wg(d)(w_ind))
            w(d)(w_ind + 1) = w(d)(w_ind + 1) - eta * g2 / sqrt(wg(d)(w_ind + 1))
            d = d +1
          }
          w_ind = w_ind + 2
          d = d+1
        } else {
          var d = 0
          while(d < k ) {
            t = t + w(d)(w_ind)*w(d)(w_ind+1)*v // w1 * w2 * xi * xj * kappa / ||x||
            d = d +1
          }
        } // do_update
        j = j + 1
      } // j < i_max
      i = i + 1
    } // i < i_max

    return t // for update t=0
  }
}
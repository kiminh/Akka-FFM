import akka.actor.{Actor, ActorLogging, ActorRef}

object Loss {

  case class UpdateLoss(x:Types.mtype)

  case object PrintNormalizedLoss

}

class Loss(lines:Int, masterAct:ActorRef) extends Actor with ActorLogging {
  var loss:Types.mtype = Types.toMtype(0.0)
  var count:Int = 0
  val print_every:Int = 1
  def receive = {

    case Loss.UpdateLoss(x:Types.mtype) => {
      count = count + 1
      loss = loss + x
      if(count%print_every==0 || count == 1) {
        val nloss: Types.mtype = loss / count
        log.info(f"Normalized Loss: ${nloss}%1.6f | Percentage: ${1.0*count/lines}%1.4f | Count: ${count} ")
      }

      /* After we process all lines we can finish work, notify master and kill all actors */
      if (lines==count){
        masterAct ! Master.WorkersFinished
      }
    }

    case Loss.PrintNormalizedLoss => {val nloss:Types.mtype = loss/count; println(s"Normalized Loss: ${nloss}")}
  }
}
import akka.actor.{Actor, ActorLogging, ActorRef}

class Terminator(app: ActorRef) extends Actor with ActorLogging {
  context watch app
  app ! Master.InitParameters
  log.info("Started")
  def receive: Receive = {
    case Master.JobDone => {log.info("Finished Computing!!!!!");context.stop(self)}
  }
}

import java.io._
import akka.actor.{Actor, ActorLogging}
import breeze.linalg.{SliceVector, DenseMatrix => BDM}
import breeze.stats.distributions.ContinuousDistr

object Parameters {

  case object RequestParameters

  case class UpdateWeight(i:Int,j:Int,v:Types.mtype)

  case class RequestWeights(x:List[(Int,Int)])

  case class FetchWeights(x:SliceVector[(Int,Int),Types.mtype])

  case class RequestGradient(x:List[(Int,Int)])

  case object Init

  case class WriteWeights(file_name:String)

  case class WriteGradient(file_name:String)

  case class LoadWeights(file_name:String)

  case class LoadGradients(file_name:String)

  case object PrintWeights
}


class Parameters(   eta: Types.mtype
                    , lambda: Types.mtype
                    , l:Int
                    , n:Int
                    , m:Int
                    , k:Int
                    , weightsInitDist: ContinuousDistr[Double]
                    , epochs:Int
                ) extends Actor with ActorLogging {

  var weights: Array[BDM[Types.mtype]] = _
  var gradient: Array[BDM[Types.mtype]] = _

  def using[T <: Closeable, R](resource: T)(block: T => R): R = {
    try { block(resource) }
    finally { resource.close() }
  }

  def readParamFile(file_name:String): Array[BDM[Types.mtype]] ={
    var matrix: Array[BDM[Types.mtype]] = Array.empty[BDM[Types.mtype]]
    val bufferedSource = io.Source.fromFile(file_name)
    for (line <- bufferedSource.getLines()) {
      val cols = line.split(",").map(x => Types.toMtype(x.trim))
      matrix = matrix :+ new BDM[Types.mtype](m, n, cols)
    }
    matrix
  }

  def receive = {

    case Parameters.RequestWeights(x) => {
      sender ! weights.map(_ (x))
    }

    case Parameters.RequestGradient(x) => {
      sender ! gradient.map(_ (x))
    }

    case Parameters.Init => {
      log.info("Initializing Parameters: weights arrays")
      weights = Array.fill[BDM[Types.mtype]](k)(new BDM[Types.mtype](m, n, Array.fill(n * m)(Types.toMtype(weightsInitDist.draw))))
      // gradient is of size n*m*k
      log.info("Initializing Parameters: gradient arrays")
      gradient = Array.fill[BDM[Types.mtype]](k)(new BDM[Types.mtype](m, n, Array.fill(n * m)(Types.toMtype(1.0))))
      // init is blocking, send back msg that its done
      sender ! "done"
    }

    case Parameters.WriteWeights(file_name) => {
      log.info(" == Parameters.WriteWeights == ")
      using(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file_name)))) {
        writer =>
          weights.foreach(x => {
            x.map(y => writer.write(y.toString + ","))
            writer.write("\n")
          }
          )
      }
      sender ! "done"
    }

    case Parameters.WriteGradient(file_name) => {
      log.info(" == Parameters.WriteGradient == ")
      using(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file_name)))) {
        writer =>
          gradient.foreach(x => {
            x.map(y => writer.write(y.toString + ","))
            writer.write("\n")
          }
          )
      }
      sender ! "done"
    }

    /* Loading weights/gradients from files. They are saved as csv, comma delimited
       when reading, the size of Array is m,n. It is Array of DenseMatrix, that is
       why reading is so strange.

       Loading is blocking, so we send back string msg to confirm that job is done.
     */

    case Parameters.LoadWeights(file_name) => {
      log.info(" == Parameters.LoadWeights == ")
      weights = readParamFile(file_name)
      sender ! "done"
    }

    case Parameters.LoadGradients(file_name) => {
      log.info(" == Parameters.LoadGradients == ")
      gradient = readParamFile(file_name)
      sender ! "done"
    }

    case Parameters.PrintWeights =>
      {
        log.info(" == Parameters.PrintWeights == ")
        println(weights.size)
        weights.foreach(println)
        sender ! "done"
      }
  }
}

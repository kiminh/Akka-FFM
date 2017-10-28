import breeze.linalg.{DenseVector => BDV}

/*
  Just in case we wanted to change type from Float to something else
  it can be easily done here. As mtype is used everywhere.
  */
object Types {
  type ActivationFunction = BDV[Float] => BDV[Float]
  type mtype = Float
  def toMtype(x: Double):mtype ={
    x.toFloat
  }
  def toMtype(x: String):mtype ={
    x.toFloat
  }
  def toMtype(x: Float):mtype ={
    x
  }
}
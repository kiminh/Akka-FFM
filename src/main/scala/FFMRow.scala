import breeze.linalg.{Vector => BV}

case class FFMRow(@transient label:Types.mtype, @transient features:BV[FFMNode])


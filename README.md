# Akka-FFM
Akka implementation of Field Aware Factorization Machines

It is an Akka (scala) implementation of 
https://www.csie.ntu.edu.tw/~cjlin/libffm/
https://arxiv.org/abs/1701.04099

It is still missing prediction part but it saves gradients and weights to txt files. To estimate model on your own data just change file_name in AkkaFFM.scala.

![downpour sgd](https://github.com/mpekalski/AkkaDistBelief/raw/master/images/downpour_sgd.png)

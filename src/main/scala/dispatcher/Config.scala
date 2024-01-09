package dispatcher

case class AxiConfig(){

  val addrWid = 32
  val dataWid = 512
  val idWid = 4

}

case class DispatcherConfig(){

  val matrixSize = 2000
  val blockSize = 64

  //AXI4
  val size = 512

  //StreamFifo
  val fifoWidth = 512
  val vexSwitchRegWidth = 16
  val edgeByteLen = 2
  val vexBigLineThreshold = math.ceil(matrixSize.toFloat/blockSize).toInt

}

case class PeConfig(){
  val peColumnNum = 4
  val peNumEachColumn = 8
  val peColumnWid = 128

}

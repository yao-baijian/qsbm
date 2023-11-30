package dispatcher

case class DispatcherConfig(){

  val matrixSize = 800
  val blockSize = 64

  //AXI4
  val size = 128

  //StreamFifo
  val fifoWidth = 16
  val vexSwitchRegWidth = 16
  val edgeByteLen = fifoWidth/8
  val vexBigLineThreshold = math.ceil(matrixSize/blockSize).toInt

}

case class PeConfig(){
  val peColumnNum = 4
  val peNumEachColumn = DispatcherConfig().size/DispatcherConfig().fifoWidth

}

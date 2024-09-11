package dispatcher

case class Config(){
  //AXI4
  val axi_width = 512
  val edge_width = 128
  val addrWid = 32
  val idWid = 4
  //StreamFifo
  val vexSwitchRegWidth = 16
  val edgeByteLen = 2
  val vexPeColumnNumFifoWidth = 2
  val initBase = 0
  val initCol = 0
  val initOffset = 0
  val pe_num = 4
}

package dispatcher

case class Config() {
  val axi_width   = 512
  val edge_width  = 128
  val addrWid     = 32
  val idWid       = 4
  val pe_num      = 4
  val pe_thread   = 8
}

package ip

import spinal.core._

class PllBB extends BlackBox{
  val io = new Bundle{
    val clk_in   = in Bool()
    val resetn = in Bool()
    val clk_fast = out Bool()
    val locked = out Bool()
  }
  noIoPrefix()
}

package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
import scala.math._

case class DualModeReg(config: PeConfig) extends Component{

    val io = new Bundle{
        val srst        = in Bool()
        val in_stream   = slave Stream(Bits(config.axi_extend_width bits))
        val rd_addr     = in UInt (1 bits)
        val rd_data     = out Bits (config.axi_extend_width bits)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val vertex_mem  = Mem(Bits(config.axi_extend_width bits), 2)
    val wr_pointer  = Reg(UInt(1 bits)) init 0
    val ready       = Reg(Bool()) init True

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    io.in_stream.ready := ready

    when(wr_pointer === 1) {
        ready := False
    } elsewhen (io.srst) {
        ready := True
    }

    when(io.in_stream.valid && io.in_stream.ready) {
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }

    vertex_mem.write(
        enable = io.in_stream.valid && io.in_stream.ready,
        address = wr_pointer,
        data = io.in_stream.payload
    )

    io.rd_data := vertex_mem.readAsync(io.rd_addr)
}
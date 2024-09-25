package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
import scala.math._

case class DualModeReg() extends Component{

    val config = PEConfig

    val io = new Bundle{
        val in_stream   = slave Stream(Bits(config.axi_extend_width bits))
        val rd_addr     = in UInt (4 bits)
        val rd_data     = out Bits (config.axi_extend_width bits)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val vertex_mem  = Mem(Bits(config.axi_extend_width bits), 16)
    val wr_pointer  = Reg(UInt(4 bits))  init 0
//    val ready       = Reg(Bool())               init True
    io.in_stream.ready := True

    when(io.in_stream.valid && wr_pointer =/= 15 ) {
        wr_pointer := wr_pointer + 1
    } elsewhen (wr_pointer === 15) {
        wr_pointer := 0
    }

    vertex_mem.write(
        enable = io.in_stream.valid && io.in_stream.ready,
        address = wr_pointer,
        data = io.in_stream.payload
    )

    io.rd_data := vertex_mem.readAsync(io.rd_addr)
}
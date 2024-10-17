package pe

import spinal.core._
import spinal.lib._
import cfg._

import scala.language.postfixOps
import scala.math._

case class DualModeReg() extends Component{

    val config = Config

    val io = new Bundle{
        val in_stream   = slave Stream(Bits(config.axi_width bits))
        val rd_addr     = in UInt (config.ram_addr_width bits)
        val rd_data     = out Bits (config.axi_width bits)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val vertex_mem  = Mem(Bits(config.axi_width bits), config.ram_depth)
    val wr_pointer  = Reg(UInt(config.ram_addr_width bits))  init 0
    io.in_stream.ready := True

    when(io.in_stream.valid && wr_pointer =/= config.ram_depth - 1 ) {
        wr_pointer := wr_pointer + 1
    } elsewhen (wr_pointer === config.ram_depth - 1) {
        wr_pointer := 0
    }

    vertex_mem.write(
        enable = io.in_stream.valid && io.in_stream.ready,
        address = wr_pointer,
        data = io.in_stream.payload
    )

    io.rd_data := vertex_mem.readAsync(io.rd_addr)
}
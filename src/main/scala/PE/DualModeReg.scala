package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
import scala.math._

case class DualModeReg(config: PeConfig) extends Component{

    val io = new Bundle{
        val srst        = in Bool()
        val swap_done   = in Bool()
        // TODO connect to stream top
        val in_stream   = slave Stream(Bits(config.axi_extend_width bits))
        val rd_addr     = in UInt (4 bits)
        val rd_data     = out Bits (config.axi_extend_width bits)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val vertex_mem  = Mem(Bits(config.axi_extend_width bits), 2 * 8)
    val wr_pointer  = Reg(UInt(4 bits)) init 0
    val ready       = Reg(Bool()) init True
    // TODO this only support size 64 * 32 = 2048
    val vertex_reg_lowerbound = Reg(UInt(6 bits)) init 0
    val vertex_reg_en         = Reg(Bits(config.thread_num bits)) init 1
    val vertex_reg_cnt        = Reg(UInt(6 bits)) init 0
    val vertex_reg_sel        = Reg(UInt(3 bits)) init 0

    when(io.swap_done) {
        vertex_reg_lowerbound := vertex_reg_lowerbound + 8
    } elsewhen (io.srst) {
        vertex_reg_lowerbound := 0
    }

    when(io.in_stream.valid && io.in_stream.payload === 0) {
        vertex_reg_cnt := vertex_reg_cnt + 1
    } elsewhen (io.swap_done || io.srst) {
        vertex_reg_cnt := 0
    }

    when(vertex_reg_cnt === vertex_reg_lowerbound) {
        vertex_reg_en := 0x00000001
        vertex_reg_sel := 0
    } elsewhen (io.in_stream.valid && io.in_stream.payload === 0 && vertex_reg_en =/= 0) {
        vertex_reg_en := vertex_reg_en |<< 1
        vertex_reg_sel := vertex_reg_sel + 1
    }

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    io.in_stream.ready := ready

    when(vertex_reg_en === 0) {
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
        address =  wr_pointer,
        data = io.in_stream.payload
    )

    io.rd_data := vertex_mem.readAsync(io.rd_addr)
}
package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
import scala.math._

case class DualModeReg(config: PeConfig) extends Component{

    val io = new Bundle{
        val srst        = in Bool()
        val in_stream   = slave Stream(Bits(config.axi_extend_width bits))
    }

    val io_gather_pe = new Bundle {
        val wr_valid    = in Bool()
        val wr_addr     = in UInt (config.addr_width bits)
        val wr_data     = in Bits (config.data_width bits)
        val rd_addr     = in UInt (config.addr_width bits)
        val rd_data     = out Bits (config.data_width bits)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val vertex_reg  = Vec(Reg(Bits(config.data_width bits)) init 0, config.matrix_size)
    val wr_pointer  = Reg(UInt(config.vertex_read_pointer_size bits)) init 0
    val ready       = Reg(Bool()) init True

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    io.in_stream.ready := ready

    when(wr_pointer === config.vertex_read_cnt_max) {
        ready := False
    } elsewhen (io.srst) {
        ready := True
    }

    when(io.in_stream.valid && io.in_stream.ready) {
        for (i <- 0 until config.vertex_write_slice) {
            vertex_reg((wr_pointer * config.vertex_write_slice) (5 downto 0) + i) := io.in_stream.payload(config.data_width * (i + 1) - 1 downto config.data_width * i)
        }
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }

    when (io_gather_pe.wr_valid) {
        vertex_reg(io_gather_pe.wr_addr) := io_gather_pe.wr_data
    }
    io_gather_pe.rd_data := vertex_reg(io_gather_pe.rd_addr)
}
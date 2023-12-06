package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
import scala.math._

case class DualModeReg(config: VertexConfig) extends Component{

    val io = new Bundle{
        val srst   = in Bool()
        val in_stream = slave Stream(Bits(config.stream_width bits))
    }
// Todo: add vertex write back

    val io_gather_pe = new Bundle {
        val wr_valid = in Bool()
        val wr_addr = in UInt (config.addr_width bits)
        val wr_data = in Bits (config.data_width bits)

        val rd_addr = in UInt (config.addr_width bits)
        val rd_data = out Bits (config.data_width bits)
    }

    val vertex_reg = Vec(Reg(Bits(config.data_width bits)) init(0), pow(2,config.addr_width).toInt)
    val wr_pointer = Reg(UInt(3 bits)) init 0

    val rdy = Reg(Bool()) init True

    when(wr_pointer === 7) {
        rdy := False
    } elsewhen (io.srst) {
        rdy := True
    }
  
    io.in_stream.ready := rdy

    when(io.in_stream.valid && io.in_stream.ready) {
        for (x <- 0 until 8) {
            vertex_reg((wr_pointer*8) (5 downto 0) + x) := io.in_stream.payload(16 * (x + 1) - 1 downto 16 * x)
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
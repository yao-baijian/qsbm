package PE

import spinal.core._
import spinal.lib._

case class DualModeReg(

      stream_width: Int = 128,
      reg_depth: Int = 64,
      addr_width: Int = 6,
      data_width: Int = 16

) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(stream_width bits))
        val rd_addr   = in UInt(addr_width bits)
        val pe_done   = in Bool()

        val rd_data   = out Bits(data_width bits)
        val reg_full  = out Bool()
        val mode  = out Bool()
    }

    val io_gather_pe = new Bundle {
        val wr_valid = out Bits(addr_width bits)
        val wr_addr = out Bits(addr_width bits)
        val wr_data = out Bits(data_width bits)
        val rd_addr = out Bits(addr_width bits)
        val rd_data = in Bits(data_width bits)
    }


    val vertex_reg = Vec(Reg(UInt(data_width bits)) init(0), reg_depth)
    val wr_pointer = Reg(UInt(3 bits)) init 0

    when (!io.mode) {
        when(wr_pointer === reg_depth - 1) {
            io.in_stream.ready := False
        } elsewhen (io.pe_done) {
            io.in_stream.ready := True
        }
    } elsewhen(io.mode) {

    }

    when ()
        when(io.in_stream.valid && io.in_stream.ready) {
            for (x <- 0 until 8) {
                vertex_reg(wr_pointer + x) := io.in_stream.payload(16 * (x + 1) - 1 downto 16 * x)
            }
            wr_pointer := wr_pointer + 1
        } otherwise {
            wr_pointer := 0
        }

    io.reg_full := !io.in_stream.ready



    io.rd_data := vertex_reg(io.rd_addr)
}
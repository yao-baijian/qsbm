package PE

import spinal.core._
import spinal.lib._

case class GlobalReg(config: GlobalRegConfig) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(config.stream_width bits))

        val rd_addr   = Vec(in UInt(config.addr_width bits), 8)
        val rd_data   = Vec(out Bits(config.data_width bits), 8)

        val need_new_vertex   = in Bool()
        val reg_full  = out Bool()
    }

    val vertex_reg = Vec(Reg(UInt(config.data_width bits)) init(0), config.reg_depth)
    val wr_pointer = Reg(UInt(3 bits)) init 0

    when (wr_pointer === config.reg_depth - 1) {
        io.in_stream.ready := False
    } elsewhen (io.need_new_vertex) {
        io.in_stream.ready := True
    }

    io.reg_full := !io.in_stream.ready

    when(io.in_stream.valid && io.in_stream.ready) {
        for (x <- 0 until 8) {
            vertex_reg(wr_pointer + x) := io.in_stream.payload(16 * (x + 1) - 1 downto 16 * x )
        }
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }

    io.rd_data := vertex_reg(io.rd_addr)
}
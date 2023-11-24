package PE

import spinal.core._
import spinal.lib._

case class DualPortReg(config:RegConfig) extends Component{

    val io = new Bundle{
        val wr_valid  = in Bool()
        val wr_addr   = in UInt(config.addr_width bits)
        val wr_data   = in Bits(config.data_width bits)
         val rd_data   = out Bits(config.data_width bits)
        val rd_addr   = in UInt(config.addr_width bits)
    }

    val dual_port_reg = Vec(Reg(UInt(config.data_width bits)) init(0), config.reg_depth)

    when (io.wr_valid) {
        dual_port_reg(io.wr_addr) := io.wr_data
    }

    io.rd_data := dual_port_reg(io.rd_addr)
}

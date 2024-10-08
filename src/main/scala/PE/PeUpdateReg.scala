package PE

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps
case class PeUpdateReg() extends Component {

    val config = PEConfig

    val io = new Bundle {
        val srst        = in Bool()
        val wr_tag      = in UInt (3 bits)
        val wr_valid    = Vec(in Bool(), config.thread_num)
        val wr_addr     = Vec(in UInt (6 bits), config.thread_num)
        val wr_data     = Vec(in Bits (config.spmv_w bits), config.thread_num)
        val rd_tag      = in UInt (3 bits)
        val rd_addr     = Vec(in UInt (6 bits), config.thread_num)
        val rd_data     = Vec(out Bits (config.spmv_w bits), config.thread_num)
        val update_valid= in Bool()
        val update_ptr  = in UInt(4 bits)
        val mem_wire    = Vec(out SInt(config.spmv_w bits), 32)
    }

    val update_reg = Vec(Vec(Reg(Bits(config.spmv_w bits)) init 0, config.matrix_size), config.thread_num) simPublic()

    for (k <- 0 until config.matrix_size) {
        when((io.wr_addr(0) === k) && io.wr_valid(0)) {
            update_reg(io.wr_tag)(k) := io.wr_data(0)
        } elsewhen ((io.wr_addr(1) === k) && io.wr_valid(1)) {
            update_reg(io.wr_tag)(k) := io.wr_data(1)
        } elsewhen ((io.wr_addr(2) === k) && io.wr_valid(2)) {
            update_reg(io.wr_tag)(k) := io.wr_data(2)
        } elsewhen ((io.wr_addr(3) === k) && io.wr_valid(3)) {
            update_reg(io.wr_tag)(k) := io.wr_data(3)
        } elsewhen ((io.wr_addr(4) === k) && io.wr_valid(4)) {
            update_reg(io.wr_tag)(k) := io.wr_data(4)
        } elsewhen ((io.wr_addr(5) === k) && io.wr_valid(5)) {
            update_reg(io.wr_tag)(k) := io.wr_data(5)
        } elsewhen ((io.wr_addr(6) === k) && io.wr_valid(6)) {
            update_reg(io.wr_tag)(k) := io.wr_data(6)
        } elsewhen ((io.wr_addr(7) === k) && io.wr_valid(7)) {
            update_reg(io.wr_tag)(k) := io.wr_data(7)
        }
    }

    for (i <- 0 until 8) {
        io.rd_data(i) := update_reg(io.rd_tag)(io.rd_addr(i))
    }

    for (i <- 0 until 8) {
        for (j <- 0 until 64) {
            when(io.srst) {
                update_reg(i)(j) := 0
            }
        }
    }

    for (i <- 0 until 32) {
        io.mem_wire(i) := io.update_valid ? update_reg(io.update_ptr(3 downto 1))((io.update_ptr(0).asUInt * 32 + i).resize(6)).asSInt | 0
    }

}

package PE

import spinal.core._
import spinal.lib.fsm._
import spinal.lib._

import scala.language.postfixOps

case class GatherPeCore() extends Component {

    val config = PEConfig

    val io = new Bundle {
        val swap_done           = in Bool()
        val spmv_result         = in Bits(config.spmv_w * config.ge_thread bits)
        val vertex_rd_data      = in Bits (config.axi_extend_width bits)
        val gather_pe_done      = out Bool()
        val writeback_valid     = out Bool()
        val writeback_payload   = out Bits(config.axi_extend_width bits)
        val rd_addr             = out UInt (4 bits)
        val itr_cnt             = in  UInt (16 bits)
        val qsb_cfg 		        = slave(qsbConfig())
    }

    val ai_init    = SFix(16 exp, 32 bits)
    val ai_incr    = SFix(16 exp, 32 bits)
    val xi         = SFix(16 exp, 32 bits)
    val itr_cnt    = SFix(0  exp, 16 bits)
    val ai         = SFix(16 exp, 32 bits)

    val h1_valid            = Reg(Bool()) init False
    val vertex_rd_addr_h1   = Reg(UInt (4 bits)) init 0

    val h2_valid            = Reg(Bool()) init False
    val spmv_result_h2      = Vec(Reg(SFix(0 exp, config.spmv_w bits)) init 0, config.ge_thread)
    val y_old_h2            = Vec(Reg(SFix(0 exp, config.xy_width bits)) init 0, config.ge_thread)
    val x_old_h2            = Vec(Reg(SFix(0 exp, config.xy_width bits)) init 0, config.ge_thread)
    val y_new_h2            = Vec(SFix(16 exp, 32 bits), config.ge_thread)

    val h3_valid            = Reg(Bool()) init False
    val y_new_h3            = Vec(Reg(SFix(16 exp, 32 bits)) init 0, config.ge_thread)
    val x_old_h3            = Vec(Reg(SFix(0 exp, config.xy_width bits)) init 0, config.ge_thread)
    val x_new_h3            = Vec(SFix(16 exp, 32 bits), config.ge_thread)
    val x_new_cliped_h3     = Vec(SInt(config.xy_width bits), config.ge_thread)
    val y_new_cliped_h3     = Vec(SInt(config.xy_width bits), config.ge_thread)

    val h4_valid            = Reg(Bool()) init False
    val vertex_wb_data_h4   = Reg(Bits (config.axi_extend_width bits)) init 0

    val gather_pe_done      = Reg(Bool()) init True
    val gather_pe_busy      = Reg(Bool()) init False

    io.gather_pe_done   := gather_pe_done
    ai_init.raw         := io.qsb_cfg.ai_init.asSInt
    ai_incr.raw         := io.qsb_cfg.ai_incr.asSInt
    xi.raw              := io.qsb_cfg.xi.asSInt
    itr_cnt.raw         := io.itr_cnt.asSInt

    val gather_pe_fsm = new StateMachine {

        val IDLE        = new State with EntryPoint
        val OPERATE     = new State

        IDLE
          .whenIsActive {
              when(io.swap_done) {
                  gather_pe_done := False
                  gather_pe_busy := True
                  h1_valid       := True
                  goto(OPERATE)
              }
          }
        OPERATE
          .whenIsActive {
            when (vertex_rd_addr_h1 === 15) {
                gather_pe_busy := False
                h1_valid       := False
            }
            when(!gather_pe_busy && h4_valid === False) {
                gather_pe_done  := True
                goto(IDLE)
            }
         }
    }

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------

    when (h1_valid) {
        vertex_rd_addr_h1 := vertex_rd_addr_h1 + 1
    } otherwise {
        vertex_rd_addr_h1 := 0
    }

    io.rd_addr       := vertex_rd_addr_h1

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) * x_old + beta * updated value) * dt
// -----------------------------------------------------------

    when (h1_valid) {
        for (i <- 0 until config.ge_thread) {
            spmv_result_h2(i).raw  := io.spmv_result((i + 1) * config.spmv_w - 1 downto i * config.spmv_w).asSInt
            x_old_h2(i).raw := io.vertex_rd_data((i + 1) * 16 - 9 downto i * 16).asSInt
            y_old_h2(i).raw := io.vertex_rd_data((i + 1) * 16 - 1 downto (i + 1) * 16 - 8).asSInt
        }
        h2_valid := True
    } otherwise {
        for (i <- 0 until config.ge_thread) {
            spmv_result_h2(i) := 0
            y_old_h2(i) := 0
            x_old_h2(i) := 0
        }
        h2_valid := False
    }

    ai := (ai_incr * itr_cnt - ai_init).truncated(16 exp, 32 bits)

    // dt = 0.25
    for (i <- 0 until config.ge_thread) {
        y_new_h2(i) := (((ai * x_old_h2(i)) + (xi * spmv_result_h2(i))).truncated(16 exp, 32 bits) >>| 2 ) + y_old_h2(i)
    }

    val dbg_1 = ai * x_old_h2(0)
    val dbg_2 = (xi * spmv_result_h2(0)).truncated(16 exp, 32 bits)

    // -----------------------------------------------------------
    // pipeline h3: x_new = x_old + y_new * dt
    //              y_comp[np.abs(x_comp) > 1] = 0
    //              np.clip(x_comp,-1, 1)
    // -----------------------------------------------------------

    when(h2_valid) {
        h3_valid := True
        for (i <- 0 until config.ge_thread) {
            y_new_h3(i) := y_new_h2(i)
            x_old_h3(i) := x_old_h2(i)
        }
    } otherwise {
        h3_valid := False
        for (i <- 0 until config.ge_thread) {
            y_new_h3(i) := 0
            x_old_h3(i) := 0
        }
    }
    for (i <- 0 until config.ge_thread) {
        x_new_h3(i) := (x_old_h3(i) + y_new_h3(i) >>| 2)
        x_new_cliped_h3(i) := (x_new_h3(i) > config.up_bound) ? S(config.up_bound) | ((x_new_h3(i) < config.lo_bound) ? S(config.lo_bound) | x_new_h3(i).toSInt(7 downto 0) )
        y_new_cliped_h3(i) := ((x_new_h3(i) < config.up_bound) & (x_new_h3(i) > config.lo_bound)) ? y_new_h3(i).toSInt(7 downto 0) | S(0)
    }
// -----------------------------------------------------------
// pipeline h5: write back
// -----------------------------------------------------------

    when (h3_valid) {
        h4_valid                := True
        for (i <- 0 until config.ge_thread) {
            vertex_wb_data_h4((i+1)*16 - 1 downto i*16) := x_new_cliped_h3(i) ## y_new_cliped_h3(i)
        }
    } otherwise {
        h4_valid                := False
        vertex_wb_data_h4       := 0
    }

    io.writeback_payload  := vertex_wb_data_h4
    io.writeback_valid    := h4_valid

}


package PE

import spinal.core._
import spinal.lib.fsm._
import spinal.lib._
import spinal.lib.pipeline._

import scala.language.postfixOps

case class Ge() extends Component {

  val config = PEConfig

  val io = new Bundle {
    val swap_done           = in Bool()
    val spmv_result         = in Bits(config.spmv_w * config.ge_thread bits)
    val vertex_rd_data      = in Bits (config.axi_extend_width bits)
    val gather_pe_done      = out Bool() setAsReg() init True
    val writeback_valid     = out Bool()
    val writeback_payload   = out Bits(config.axi_extend_width bits) setAsReg() init 0
    val rd_addr             = out UInt (4 bits) setAsReg() init 0
    val itr_cnt             = in  UInt (16 bits)
    val qsb_cfg 		        = slave(qsbConfig())
  }

  val ai_init, ai_incr    = SFix(16 exp, 32 bits)
  val xi, ai              = SFix(16 exp, 32 bits)
  val itr_cnt             = SFix(0  exp, 16 bits)
  val h1_valid            = Reg(Bool()) init False
  val spmv_result         = Stageable(Vec(SFix(0 exp, config.spmv_w bits), config.ge_thread))
  val y_old, x_old        = Stageable(Vec(SFix(0 exp, config.xy_width bits), config.ge_thread))
  val tmp_0, tmp_1, tmp_2 = Stageable(Vec(SFix(16 exp, 32 bits), config.ge_thread))
  val y_new, x_new        = Stageable(Vec(SFix(16 exp, 32 bits), config.ge_thread))
  val x_new_clp, y_new_clp= Stageable(Vec(SInt(config.xy_width bits), config.ge_thread))
  val gather_pe_busy      = Reg(Bool()) init False

  ai_init.raw         := io.qsb_cfg.ai_init.asSInt
  ai_incr.raw         := io.qsb_cfg.ai_incr.asSInt
  xi.raw              := io.qsb_cfg.xi.asSInt
  itr_cnt.raw         := io.itr_cnt.asSInt
  ai                  := (ai_incr * itr_cnt - ai_init).truncated(16 exp, 32 bits)

  val ge_fsm = new StateMachine {

    val IDLE        = new State with EntryPoint
    val OPERATE     = new State

    IDLE
      .whenIsActive {
        when(io.swap_done) {
          io.gather_pe_done := False
          gather_pe_busy := True
          h1_valid       := True
          goto(OPERATE)
        }
      }
    OPERATE
      .whenIsActive {
        when (io.rd_addr === 15) {
          gather_pe_busy := False
          h1_valid       := False
        }
        when(!gather_pe_busy && ge_pip.s3.valid === False) {
          io.gather_pe_done  := True
          goto(IDLE)
        }
      }
  }

  val ge_pip = new Pipeline {

    //-----------------------------------------------------------
    // pipeline s0: read updated value (J @ X_comp) and vertex ram (x_old)
    //-----------------------------------------------------------

    val s0 =new Stage {
      this.valid := h1_valid
      io.rd_addr := h1_valid ? (io.rd_addr + 1) | 0
    }

    // -----------------------------------------------------------
    // pipeline s1: y_new = y_old + ((-1 + alpha) * x_old + beta * updated value) * dt
    // -----------------------------------------------------------

    val s1 = new Stage(Connection.M2S()) {
      for (i <- 0 until config.ge_thread) {
        spmv_result(i).raw  := io.spmv_result((i + 1) * config.spmv_w - 1 downto i * config.spmv_w).asSInt
        x_old(i).raw        := io.vertex_rd_data((i + 1) * 16 - 9 downto i * 16).asSInt
        y_old(i).raw        := io.vertex_rd_data((i + 1) * 16 - 1 downto (i + 1) * 16 - 8).asSInt
        tmp_0(i)            := (ai * x_old(i)).truncated(16 exp, 32 bits)
        tmp_1(i)            := (xi * spmv_result(i)).truncated(16 exp, 32 bits)
      }
    }

    val s2 = new Stage(Connection.M2S()) {
      for (i <- 0 until config.ge_thread) {
        tmp_2(i)     :=   (tmp_0(i) + tmp_1(i)).truncated(16 exp, 32 bits) >>| 2
      }
    }

    val s3 = new Stage(Connection.M2S()) {
      for (i <- 0 until config.ge_thread) {
        y_new(i)     := tmp_2(i) + y_old(i)
      }
    }

    // -----------------------------------------------------------
    // pipeline s2: x_new = x_old + y_new * dt
    //              y_comp[np.abs(x_comp) > 1] = 0
    //              np.clip(x_comp,-1, 1)
    // -----------------------------------------------------------

    val s4 = new Stage(Connection.M2S()){
      for (i <- 0 until config.ge_thread) {
        x_new(i) := (x_old(i) + y_new(i) >>| 2)
        x_new_clp(i) := (x_new(i) > config.up_bound) ? S(config.up_bound) | ((x_new(i) < config.lo_bound) ? S(config.lo_bound) | x_new(i).toSInt(7 downto 0) )
        y_new_clp(i) := ((x_new(i) < config.up_bound) & (x_new(i) > config.lo_bound)) ? y_new(i).toSInt(7 downto 0) | S(0)
      }
    }
    // -----------------------------------------------------------
    // pipeline s3: write back
    // -----------------------------------------------------------

    val s6 = new Stage(Connection.M2S()){
      for (i <- 0 until config.ge_thread) {
        io.writeback_payload((i+1)*16 - 1 downto i*16) := x_new_clp(i) ## y_new_clp(i)
      }
    }

  }

  ge_pip.build()

  io.writeback_valid  := ge_pip.s3.internals.input.valid

}


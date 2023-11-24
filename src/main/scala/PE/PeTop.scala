package PE

import spinal.core.{Reg, Vec, _}
import spinal.lib._
import spinal.lib.fsm._


case class PeTop() extends Component {

    val io = new Bundle {
        val last_update = in Bool()
    }

    val y = new Array[Reg(Bits(32 bits)) init 1](8)
    val z = new Array[PeBundle](8)

    val reg_config = RegConfig()
    val pe_bundle_config = PeBundleConfig()
    val gather_pe_bundle_config = GatherPeConfig()

    val pe_bundle_array = new Array[PeBundle](4)

    for (i <- 0 until 4) {
        pe_bundle_array(i) = PeBundle(pe_bundle_config)
        pe_bundle_array(i).setName("pe_bundle" + i.toString)
    }

//    val vFifoDataRegLast =  Array.tabulate(4)(_ => Reg(Bits(32 bits)) init 1)

    val pe_bundle1_reg1 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg2 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg3 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg4 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg5 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg6 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg7 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
    val pe_bundle1_reg8 = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)

    val pe_bundle2_reg1 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg2 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg3 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg4 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg5 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg6 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg7 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle2_reg8 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    val pe_bundle3_reg1 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg2 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg3 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg4 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg5 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg6 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg7 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle3_reg8 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    val pe_bundle4_reg1 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg2 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg3 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg4 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg5 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg6 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg7 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
    val pe_bundle4_reg8 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    val reg_array = new Array[DualModeReg](16)

    for (i <- 0 until 8) {
        reg_array(i) = DualModeReg()
        reg_array(i).setName("vertex_regA_" + i.toString)
        reg_array(i+8) = DualModeReg()
        reg_array(i+8).setName("vertex_regB_" + i.toString)
    }

    val gather_pe_bundle_array = new Array[GatherPeCore](8)

    for (i <- 0 until 8) {
        gather_pe_bundle_array(i) = GatherPeCore(gather_pe_bundle_config)
        gather_pe_bundle_array(i).setName("gather_pe" + i.toString)
    }

    val update_reg1 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg1(x) := pe_bundle1_reg1(x) + pe_bundle2_reg1(x) + pe_bundle3_reg1(x) + pe_bundle4_reg1(x)
        }
    }

    val update_reg2 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg2(x) := pe_bundle1_reg2(x) + pe_bundle2_reg2(x) + pe_bundle3_reg2(x) + pe_bundle4_reg2(x)
        }
    }

    val update_reg3 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg3(x) := pe_bundle1_reg3(x) + pe_bundle2_reg3(x) + pe_bundle3_reg3(x) + pe_bundle4_reg3(x)
        }
    }

    val update_reg4 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg4(x) := pe_bundle1_reg4(x) + pe_bundle2_reg4(x) + pe_bundle3_reg4(x) + pe_bundle4_reg4(x)
        }
    }

    val update_reg5 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg5(x) := pe_bundle1_reg5(x) + pe_bundle2_reg5(x) + pe_bundle3_reg5(x) + pe_bundle4_reg5(x)
        }
    }

    val update_reg6 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg6(x) := pe_bundle1_reg6(x) + pe_bundle2_reg6(x) + pe_bundle3_reg6(x) + pe_bundle4_reg6(x)
        }
    }

    val update_reg7 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg7(x) := pe_bundle1_reg7(x) + pe_bundle2_reg7(x) + pe_bundle3_reg7(x) + pe_bundle4_reg7(x)
        }
    }

    val update_reg8 = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)

    when(need_update) {
        for (x <- 0 until 64) {
            update_reg8(x) := pe_bundle1_reg8(x) + pe_bundle2_reg8(x) + pe_bundle3_reg8(x) + pe_bundle4_reg8(x)
        }
    }




    val gather_pe_bundle = PeBundle(pe_bundle_config)

    val bundle_busy = Bool() init(False)
    bundle_busy := pe_bundle_array(1).io_state.bundle_busy || pe_bundle_array(1).io_state.bundle_busy ||
      pe_bundle_array(3).io_state.bundle_busy || pe_bundle_array(4).io_state.bundle_busy

    val update_sum_finish = Bool() init(False)
    val last_update = Bool() init(False)
    val need_update = Reg(Bool()) init(False)
    val need_gather = Reg(Bool()) init(False)

    last_update := io.last_update

    val pe_top_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val PE_BUNDLE = new State
        val UPDATE_SUM = new State
        val PE_GATHER_PE = new State
        val GATHER_PE = new State
        val WAIT_UPDATE_FINISH = new State

        IDLE
          .whenIsActive(
              when(bundle_busy) {
                  goto(PE_BUNDLE)
              }
          )

        PE_BUNDLE
          .whenIsActive {
              when(last_update && !bundle_busy) {
                  goto(UPDATE_SUM)
                  need_update := True
              }
          }
        UPDATE_SUM
          .whenIsActive {
              when(need_update) {
                  need_update := False
              }
              when(!need_update) {
                  when (pe_valid) {
                      need_gather := True
                      goto(PE_GATHER_PE)
                  } otherwise {
                      need_gather := True
                      goto(GATHER_PE)
                  }
              }
          }
        PE_GATHER_PE
          .whenIsActive {
              when(last_update && !bundle_busy && writeback_done) {
                  goto(UPDATE_SUM)
              }
          }

        GATHER_PE
          .whenIsActive {
              when(writeback_done) {
                  goto(IDLE)
              }
          }
    }





}

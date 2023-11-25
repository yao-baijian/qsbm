package PE

import spinal.core.{Reg, Vec, _}
import spinal.lib._
import spinal.lib.fsm._


case class PeTop() extends Component {

    val io = new Bundle {
        val vertex_stream = in Bool()
    }

    val reg_config = RegConfig()
    val pe_bundle_config = PeBundleConfig()
    val gather_pe_bundle_config = GatherPeCoreConfig()

    val pe_bundle_array = new Array[PeBundle](4)
    val pe_bundle_reg_group = new Array[Array[Vec[UInt]]](4)
//  val pe_bundle_reg_group =  Array.tabulate(4)(_ => Vec)
//  alternative declaration of pe_bundle_reg_group

//  PE Bundle, each with respective update reg group
    for (i <- 0 until 4) {
        pe_bundle_array(i) = PeBundle(pe_bundle_config)
        pe_bundle_array(i).setName("pe_bundle" + i.toString)
        for (j <- 0 until 8) {
            pe_bundle_reg_group(i)(j) = Vec(Reg(UInt(reg_config.data_width bits)) init(0), reg_config.reg_depth)
            pe_bundle_reg_group(i)(j).setName("pe_bundle"+ i.toString+"_reg"+j.toString)
        }

    }

//  Vertex Reg Array is used to store vertex date for gather PE
    val vertex_reg_array = new Array[DualModeReg](16)

    for (i <- 0 until 8) {
        vertex_reg_array(i) = DualModeReg()
        vertex_reg_array(i).setName("vertex_regA_" + i.toString)
        vertex_reg_array(i+8) = DualModeReg()
        vertex_reg_array(i+8).setName("vertex_regB_" + i.toString)
    }

    When ()

//  Update reg group
//  sum up 4 large pe result
    val update_reg_group = new Array[Vec[UInt]](8)

    for (i <- 0 until 8) {
        update_reg_group(i) = Vec(Reg(UInt(reg_config.data_width bits)) init (0), reg_config.reg_depth)
        update_reg_group(i).setName("update_reg" + i.toString)
    }

    when(need_update) {
        for (i <- 0 until 8) {
            for (j <- 0 until 64) {
                update_reg_group (i)(j) := pe_bundle_reg_group(1)(i)(j) + pe_bundle_reg_group(2)(i)(j) +
                                            pe_bundle_reg_group(3)(i)(j) + pe_bundle_reg_group(4)(i)(j)
            }
        }
    }

val gather_pe_bundle_array = new Array[GatherPeCore](8)

for (i <- 0 until 8) {
    gather_pe_bundle_array(i) = GatherPeCore(gather_pe_bundle_config)
    gather_pe_bundle_array(i).setName("gather_pe" + i.toString)
}

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

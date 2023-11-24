package PE

import spinal.core._
import spinal.core.internals.Operator
import spinal.lib._
import spinal.lib.fsm._


case class PeBundle(config: PeBundleConfig) extends Component {
    val io_fifo = new Bundle {
        val pe1_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe2_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe3_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe4_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe5_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe6_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe7_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
        val pe8_fifo       =   slave Stream(Bits(config.axi_width / 8 bits))
    }

    val io_global_reg =new Bundle {
        val vertex_stream = slave Stream (Bits(config.axi_width bits))
    }

    val io_last_update = new Bundle {
        val last_update   = in Bool()
    }

    val io_state = new Bundle {
        val bundle_busy = out Bool()
        val switch_done = in Bool()
    }

    val io_update_reg = new Bundle {
        val pe1_update_reg_addr = out Bool()
        val switch_done = in Bool()
    }


    val global_reg          =   GlobalReg (config.global_reg_config)

    val pe1 =   PeCore(config.pe_core_config)
    (switch_done && pe1.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe1_fifo.ready
    pe1.io_edge_fifo.edge_fifo_valid <> io_fifo.pe1_fifo.valid
    pe1.io_edge_fifo.edge_fifo_in <> io_fifo.pe1_fifo.payload

//    val pe1_update_reg = DualPortReg(config.reg_config)

    val pe2 =   PeCore(config.pe_core_config)
    (switch_done && pe2.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe2_fifo.ready
    pe2.io_edge_fifo.edge_fifo_valid <> io_fifo.pe2_fifo.valid
    pe2.io_edge_fifo.edge_fifo_in <> io_fifo.pe2_fifo.payload

//    val pe2_update_reg = DualPortReg(config.reg_config)

    val pe3 =   PeCore(config.pe_core_config)
    (switch_done && pe3.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe3_fifo.ready
    pe3.io_edge_fifo.edge_fifo_valid <> io_fifo.pe3_fifo.valid
    pe3.io_edge_fifo.edge_fifo_in <> io_fifo.pe3_fifo.payload

//    val pe3_update_reg = DualPortReg(config.reg_config)

    val pe4 =   PeCore(config.pe_core_config)
    (switch_done && pe4.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe4_fifo.ready
    pe4.io_edge_fifo.edge_fifo_valid <> io_fifo.pe4_fifo.valid
    pe4.io_edge_fifo.edge_fifo_in <> io_fifo.pe4_fifo.payload

//    val pe4_update_reg = DualPortReg(config.reg_config)

    val pe5 =   PeCore(config.pe_core_config)
    (switch_done && pe5.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe5_fifo.ready
    pe5.io_edge_fifo.edge_fifo_valid <> io_fifo.pe5_fifo.valid
    pe5.io_edge_fifo.edge_fifo_in <> io_fifo.pe5_fifo.payload

//    val pe5_update_reg = DualPortReg(config.reg_config)

    val pe6 =   PeCore(config.pe_core_config)
    (switch_done && pe6.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe6_fifo.ready
    pe6.io_edge_fifo.edge_fifo_valid <> io_fifo.pe6_fifo.valid
    pe6.io_edge_fifo.edge_fifo_in <> io_fifo.pe6_fifo.payload

//    val pe6_update_reg = DualPortReg(config.reg_config)

    val pe7 =   PeCore(config.pe_core_config)
    (switch_done && pe7.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe7_fifo.ready
    pe7.io_edge_fifo.edge_fifo_valid <> io_fifo.pe7_fifo.valid
    pe7.io_edge_fifo.edge_fifo_in <> io_fifo.pe7_fifo.payload

//    val pe7_update_reg = DualPortReg(config.reg_config)

    val pe8 =   PeCore(config.pe_core_config)
    (switch_done && pe8.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe8_fifo.ready
    pe8.io_edge_fifo.edge_fifo_valid <> io_fifo.pe8_fifo.valid
    pe8.io_edge_fifo.edge_fifo_in <> io_fifo.pe8_fifo.payload

//    val pe8_update_reg = DualPortReg(config.reg_config)


  //  Top FSM
  //  State   |     PE     |  Gather PE |
  //   AA     |     0      |     0      |
  //   BA     |     1      |     0      |
  //   BB     |     1      |     1      |
  //   AB     |     0      |     1      |           // at the end of

io_state.bundle_busy :=  pe1.io_state.pe_busy | pe2.io_state.pe_busy | pe3.io_state.pe_busy | pe4.io_state.pe_busy |
        pe5.io_state.pe_busy | pe6.io_state.pe_busy | pe7.io_state.pe_busy | pe8.io_state.pe_busy

val fifo_valid =  Bool()
fifo_valid :=   io_fifo.pe1_fifo.valid | io_fifo.pe2_fifo.valid | io_fifo.pe3_fifo.valid | io_fifo.pe4_fifo.valid |
                io_fifo.pe5_fifo.valid | io_fifo.pe6_fifo.valid | io_fifo.pe7_fifo.valid | io_fifo.pe8_fifo.valid

val switch_done = Reg(Bool())

    val pe_bundle_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val WAIT_EDGE_VERTEX = new State
        val PE = new State
        val LAST_UP_DATE = new State
        val WAIT_UPDATE_FINISH = new State

        IDLE
          .whenIsActive(
            when(io_state.bundle_busy) {
              goto(PE)
            }
          )

        WAIT_EDGE_VERTEX
          .whenIsActive {
              when(update_done) {
                  goto(PE)
              }
          }
        PE
          .whenIsActive {
              when(io_last_update.last_update) {
                  goto(LAST_UP_DATE)
              }
              when(!io_state.bundle_busy) {
                  goto(WAIT_EDGE_VERTEX)
              }
          }
        LAST_UP_DATE
          .whenIsActive {
              when(!io_state.bundle_busy) {
                  goto(WAIT_UPDATE_FINISH)
              }
          }

        WAIT_UPDATE_FINISH
          .whenIsActive {
            when(fifo_valid) {
              goto(WAIT_EDGE_VERTEX)
            } otherwise {
                goto(IDLE)
            }
          }
      }

}


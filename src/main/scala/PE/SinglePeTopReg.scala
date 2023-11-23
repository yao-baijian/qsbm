package PE

import spinal.core._
import spinal.core.internals.Operator
import spinal.lib._
import spinal.lib.fsm._

case class SinglePeTopReg(

       alpha: SInt = 1, //alpha = (np.linspace(0, 1, num_iters))
       beta: SInt = 8, // beta = (0.7 / math.sqrt(N))#
       xi_dt: SInt = 32,
       positive_boundary: SInt = 127,
       negetive_boundary: SInt = -1, // np.clip(x_comp,-1, 1)

       axi_width: Int = 128,
       edge_width: Int = 128,
       fifo_depth: Int = 64,

       matrix_size:Int = 64,
       addr_width:Int = 6,
       vertex_width:Int = 16,

       update_ram_addr_width: Int = 64,
       vertex_ram_addr_width: Int = 64,

       update_ram_depth: Int = 64,
       vertex_ram_depth: Int = 64,

       update_ram_width: Int = 32,
       vertex_ram_width: Int = 32

) extends Component {

    val io = new Bundle {
        val pe_idle = out Bool()
        val edge_stream = slave Stream (Bits(axi_width bits))
        val vertex_stream = slave Stream (Bits(axi_width bits))
        val more_block = in Bool()
    }
    val io_fifo = new Bundle {
        val pe_1_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_2_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_3_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_4_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_5_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_6_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_7_fifo       =   slave Stream(Bits(axi_width / 8 bits))
        val pe_8_fifo       =   slave Stream(Bits(axi_width / 8 bits))
    }

    val global_reg          =   GlobalReg (axi_width,
                                            matrix_size,
                                            addr_width,
                                            vertex_width)

    global_reg.io.in_stream << io.vertex_stream


//    val edge_fifo           =   Fifo( fifo_depth,
//                                      edge_width,
//                                      axi_width)
//    edge_fifo.io.in_stream  := io.edge_stream
//    edge_fifo.io.out_stream >> pe.io_edge_fifo.edge_fifo_in
//    edge_fifo.io.fifo_done  := pe.io_edge_fifo.edge_fifo_noempty
//    edge_fifo.io.rst

// To do:
// stream width doesn't fit

    val pe_1 =   PeCore()
    (switch_done && pe_1.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_1_fifo.ready
    pe_1.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_1_fifo.valid
    pe_1.io_edge_fifo.edge_fifo_in <> io_fifo.pe_1_fifo.payload

    val pe_2 =   PeCore()
    (switch_done && pe_2.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_2_fifo.ready
    pe_2.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_2_fifo.valid
    pe_2.io_edge_fifo.edge_fifo_in <> io_fifo.pe_2_fifo.payload

    val pe_3 =   PeCore()
    (switch_done && pe_3.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_3_fifo.ready
    pe_3.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_3_fifo.valid
    pe_3.io_edge_fifo.edge_fifo_in <> io_fifo.pe_3_fifo.payload

    val pe_4 =   PeCore()
    (switch_done && pe_4.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_4_fifo.ready
    pe_4.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_4_fifo.valid
    pe_4.io_edge_fifo.edge_fifo_in <> io_fifo.pe_4_fifo.payload

    val pe_5 =   PeCore()
    (switch_done && pe_5.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_5_fifo.ready
    pe_5.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_5_fifo.valid
    pe_5.io_edge_fifo.edge_fifo_in <> io_fifo.pe_5_fifo.payload

    val pe_6 =   PeCore()
    (switch_done && pe_6.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_6_fifo.ready
    pe_6.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_6_fifo.valid
    pe_6.io_edge_fifo.edge_fifo_in <> io_fifo.pe_6_fifo.payload

    val pe_7 =   PeCore()
    (switch_done && pe_7.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_7_fifo.ready
    pe_7.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_7_fifo.valid
    pe_7.io_edge_fifo.edge_fifo_in <> io_fifo.pe_7_fifo.payload

    val pe_8 =   PeCore()
    (switch_done && pe_8.io_edge_fifo.edge_fifo_ready) <> io_fifo.pe_8_fifo.ready
    pe_8.io_edge_fifo.edge_fifo_valid <> io_fifo.pe_8_fifo.valid
    pe_8.io_edge_fifo.edge_fifo_in <> io_fifo.pe_8_fifo.payload

//    pe.io.edge_fifo_in << edge_fifo.out_stream
//    pe.io.vertex_reg << global_reg.vertex_stream


    val gather_pe           =   GatherPeCore( alpha,
                                                beta,
                                                xi_dt,
                                                positive_boundary,
                                                negetive_boundary)



    val switch_vertex_ram   =   Switch( update_ram_width,
                                        update_ram_addr_width )
//    switch_vertex_ram.io_A  :=
    switch_vertex_ram.io_ramA   <>  vertex_regA.io


    val switch_update_ram   =   Switch( update_ram_width,
                                        update_ram_addr_width)







    val update_regA         =   DualPortReg (  update_ram_depth,
                                        update_ram_width,
                                        update_ram_addr_width)

    val update_regB         =   DualPortReg (  update_ram_depth,
                                        update_ram_width,
                                        update_ram_addr_width)

    val vertex_regA         =   DualPortReg (  vertex_ram_depth,
                                        vertex_ram_width,
                                        vertex_ram_addr_width)

    val vertex_regB         =   DualPortReg (  vertex_ram_depth,
                                        vertex_ram_width,
                                        vertex_ram_addr_width)



  //  Top FSM
  //  State   |     PE     |  Gather PE |
  //   AA     |     0      |     0      |
  //   BA     |     1      |     0      |
  //   BB     |     1      |     1      |
  //   AB     |     0      |     1      |           // at the end of

val pe_busy = Bool()
pe_busy :=  pe_1.io_state.pe_busy | pe_2.io_state.pe_busy | pe_3.io_state.pe_busy | pe_4.io_state.pe_busy |
            pe_5.io_state.pe_busy | pe_6.io_state.pe_busy | pe_7.io_state.pe_busy | pe_8.io_state.pe_busy
val fifo_valid =  Bool()
fifo_valid :=   io_fifo.pe_1_fifo.valid | io_fifo.pe_2_fifo.valid | io_fifo.pe_3_fifo.valid | io_fifo.pe_4_fifo.valid |
                io_fifo.pe_5_fifo.valid | io_fifo.pe_6_fifo.valid | io_fifo.pe_7_fifo.valid | io_fifo.pe_8_fifo.valid

    val switch_done = Reg(Bool())


    val top_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val PE = new State
        val PE_GATHER_PE = new State
        val GATHER_PE = new State
        val SWITCH = new State

        IDLE
          .whenIsActive(
            when(pe_busy) {
              goto(PE)
            }
          )

        PE
          .whenIsActive {
            when(!pe_busy) {
              goto(SWITCH)
            }
          }

        SWITCH
          .whenIsActive {
              when(switch_done && fifo_valid) {
                  goto(PE_GATHER_PE)
              } elsewhen(switch_done) {
                  goto(GATHER_PE)
              }
          }

        PE_GATHER_PE
          .whenIsActive {
            when(!pe_busy && gather_pe.io_state.gather_pe_busy) {
              goto(GATHER_PE)
            }
          }

        GATHER_PE
          .whenIsActive(
            when(pe_busy) {
                goto(PE_GATHER_PE)
            }.elsewhen (!gather_pe.io_state.gather_pe_busy) {
                goto(IDLE)
            }
          )
      }


}


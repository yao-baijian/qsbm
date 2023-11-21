package PE
import spinal.lib._
import spinal.lib.fsm._
import spinal.core._
import PE._

case class Pe_Top(

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
        val pe_idle         =   out Bool()
        val edge_stream     =   slave Stream(Bits(axi_width bits))
        val vertex_stream   =   slave Stream(Bits(axi_width bits))
        val more_block      =   in Bool()
    }

    val global_reg          =   Global_Reg (axi_width,
                                            matrix_size,
                                            addr_width,
                                            vertex_width)

    global_reg.io.in_stream << io.vertex_stream


    val edge_fifo           =   Fifo( fifo_depth,
                                      edge_width,
                                      axi_width)
    edge_fifo.io.in_stream  := io.edge_stream
//    edge_fifo.io.out_stream >> pe.io_edge_fifo.edge_fifo_in
    edge_fifo.io.fifo_done  := pe.io_edge_fifo.edge_fifo_noempty
//    edge_fifo.io.rst

// To do:
// stream width doesn't fit

    val pe                  =   Pe_Core(

                                        )
//    pe.io.edge_fifo_in << edge_fifo.out_stream
//    pe.io.vertex_reg << global_reg.vertex_stream


    val gather_pe           =   Gather_Pe_Core( alpha,
                                                beta,
                                                xi_dt,
                                                positive_boundary,
                                                negetive_boundary)

    val switch_vertex_ram   =   Switch( update_ram_width,
                                        update_ram_addr_width )
//    switch_vertex_ram.io_A  :=
    switch_vertex_ram.io_ramA   <>  vertex_ramA.io


    val switch_update_ram   =   Switch( update_ram_width,
                                        update_ram_addr_width)


    val update_ramA         =   Bram (  update_ram_depth,
                                        update_ram_width,
                                        update_ram_addr_width)

    val update_ramB         =   Bram (  update_ram_depth,
                                        update_ram_width,
                                        update_ram_addr_width)

    val vertex_ramA         =   Bram (  vertex_ram_depth,
                                        vertex_ram_width,
                                        vertex_ram_addr_width)

    val vertex_ramB         =   Bram (  vertex_ram_depth,
                                        vertex_ram_width,
                                        vertex_ram_addr_width)

  //  Top FSM
  //  State   |     PE     |  Gather PE |
  //   AA     |     0      |     0      |
  //   BA     |     1      |     0      |
  //   BB     |     1      |     1      |
  //   AB     |     0      |     1      |           // at the end of

  val top_fsm = new StateMachine {

    val IDLE = new State with EntryPoint
    val PE = new State
    val PE_GATHER_PE = new State
    val GATHER_PE = new State

    IDLE
      .whenIsActive(
        when(pe.io_state.pe_busy) {
          goto(PE)
        }
      )

    PE
      .whenIsActive {
        when(gather_pe.io_state.gather_pe_busy) {
          goto(GATHER_PE)
        }
      }

    PE_GATHER_PE
      .whenIsActive {
        when(!pe.io_state.pe_busy && gather_pe.io_state.gather_pe_busy) {
          goto(GATHER_PE)
        }
      }

    GATHER_PE
      .whenIsActive(
        when(pe.io_state.pe_busy) {
            goto(PE_GATHER_PE)
        }.elsewhen (!gather_pe.io_state.gather_pe_busy) {
            goto(IDLE)
        }
      )
  }


}


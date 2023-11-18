package PE

import spinal.lib._
import spinal.lib.fsm._
import spinal.core._
import PE._

case class Pe_Top(

    alpha: SInt = 1, //alpha = (np.linspace(0, 1, num_iters))
    beta: SInt = 8,  // beta = (0.7 / math.sqrt(N))#
    xi_dt: SInt = 32, 
    positive_boundary: SInt = 127,
    negetive_boundary: SInt = -1,    // np.clip(x_comp,-1, 1)

    axi_width: Int = 128,
    edge_width: Int = 128,
    fifo_depth: Int = 64,

    update_ram_addr_width: Int = 64,
    vertex_ram_addr_width: Int = 64,
    update_ram_entry_num: Int = 64,
    vertex_ram_entry_num: Int = 64,
    update_ram_entry_width: Int = 32,
    vertex_ram_entry_width: Int = 32

) extends Component {

    val io = new Bundle {
        val pe_idle         =   out Bool()
        val edge_stream     =   slave Stream(Bits(axi_width bits))
        val vertex_stream   =   slave Stream(Bits(axi_width bits))
    }

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
        when() {
          goto(OPERATE)
        }
      )

    OPERATE
      .whenIsActive {
        when() {
          goto(FINISH)
        }
      }

    WAIT_UPDATE
      .whenIsActive {
        when() {
          goto(FINISH)
        }
      }

    FINISH
      .whenIsActive(
        when() {
          goto(IDLE)
        }
      )
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

    val switch_vertex_ram   =   Switch( vertex_ram_entry_width, 
                                        10)

    val switch_update_ram   =   Switch(update_ram_entry_width, 10)

    val update_ramA         =   Bram(update_ram_entry_num, 
                                    Bits(update_ram_entry_width))

    val update_ramB         =   Bram(update_ram_entry_num, Bits(update_ram_entry_type))

    val vertex_ramA         =   Bram(vertex_ram_entry_num, Bits(vertex_ram_entry_type))

    val vertex_ramB         =   Bram(vertex_ram_entry_num, Bits(vertex_ram_entry_type))




}


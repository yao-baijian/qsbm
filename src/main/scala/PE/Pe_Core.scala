package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

case class Pe_Core(

    vertex_reg_data_width: Int = 32,
    vertex_reg_addr_width: Int = 6,
    edge_width  : Int = 32,

    update_ram_addr_width: Int = 6,
    updata_ram_data_width: Int = 32,

    vertex_ram_addr_width: Int = 6,
    vertex_ram_data_width: Int = 32

) extends Component {


    val io_edge_fifo = new Bundle {
        val edge_fifo_in = in Bits (edge_width bits)
        val edge_fifo_noempty = in Bool()
    }

    val io_vertex_reg = new Bundle {
        val vertex_reg_addr = out Bits (vertex_reg_addr_width bits)
        val vertex_reg_in = in SInt (vertex_reg_data_width bits)
        val vertex_reg_full = in Bool()
    }

    val io_update_ram = new Bundle {
        val update_ram_wr_valid = out Bool()
        val update_ram_wr_addr = out Bits (update_ram_addr_width bits)
        val update_ram_wr_data = out Bits (32 bits)

        val update_ram_rd_valid = out Bool()
        val update_ram_rd_addr = out Bits (update_ram_addr_width bits)
        val update_ram_data_old = in Bits (32 bits)
    }

    val io_to_switch = new Bundle {
        val vertex_switch_done = in Bool()
        val update_switch_done = in Bool()
    }

// wire

val switch_done = Bool() init False

val vertex_reg_data_h2		= SInt(32 bits)
val ram_data_h3             = SInt(32 bits)

// this logic is problematic
switch_done := io_to_switch.vertex_switch_done && io_to_switch.update_switch_done

// Reg Group
// h0
val update_edge_addr_pre_h0 = Reg(Bits(8 bits))

// h1
val edge_value_h1 = Reg(Bits(8 bits)) init 0
val vertex_reg_addr_h1 = Reg(Bits(10 bits)) init 0
val update_ram_addr_h1 = Reg(Bits(10 bits)) init 0
val h1_valid = Reg(Bool()) init False

// h2
val edge_value_h2 = Reg(Bits(8 bits)) init 0
val h2_valid = Reg(Bool()) init False
val updata_data_h2 = Bits(32 bits)
val update_ram_addr_h2 = Reg(Bits(6 bits))

// h3
val h3_valid = Reg(Bool()) init False
val ram_data_h3 = Reg(Bits(32 bits)) init 0
val update_ram_addr_h3 = Reg(Bits(6 bits)) init 0
// state machine

    val pe_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val FINISH = new State

        IDLE
          .whenIsActive {
            when(io_edge_fifo.edge_fifo_noempty && io_vertex_reg.vertex_reg_full) {
                goto(OPERATE)
            }
        }

        OPERATE
        .whenIsActive {
            when(h3_valid && !h2_valid) {
                goto(FINISH)
            }
        }

        FINISH
        .whenIsActive (
            when (switch_done) {
                goto(IDLE)
            }
        )
    }




//-----------------------------------------------------------
// pipeline h0
//-----------------------------------------------------------


//-----------------------------------------------------------
// pipeline h1
//-----------------------------------------------------------

    when (pe_fsm.states == pe_fsm.OPERATE && io_edge_fifo.edge_fifo_noempty) {
        vertex_reg_addr_h1  := io_edge_fifo.edge_fifo_in (31 downto 22)
        update_ram_addr_h1  := io_edge_fifo.edge_fifo_in (21 downto 12)
        edge_value_h1       := io_edge_fifo.edge_fifo_in (11 downto 0)
        h1_valid            := True
    } otherwise {
        vertex_reg_addr_h1  := 0
        update_ram_addr_h1  := 0
        edge_value_h1       := 0
        h1_valid            := False
    }

    io_update_ram.update_ram_rd_valid   := h1_valid
    io_update_ram.update_ram_rd_addr    := update_ram_addr_h1


//-----------------------------------------------------------
// pipeline h2
//-----------------------------------------------------------

    when (h1_valid) {
        edge_value_h2  		:= edge_value_h1
        update_ram_addr_h2:= update_ram_addr_h1
        h2_valid       		:= True
    } otherwise {
        edge_value_h2  		:= 0
        update_ram_addr_h1  := 0
        h2_valid       		:= False
    }

    updata_data_h2 := io_update_ram.update_ram_data_old + vertex_reg_data_h2 * ram_data_h3;

// hazard detect


//-----------------------------------------------------------
// pipeline h3
//-----------------------------------------------------------


    when (h2_valid) {
        update_ram_addr_h3	:= update_ram_addr_h2
        ram_data_h3  		:= updata_data_h2
        h3_valid     		:= True
    } otherwise {
        update_ram_addr_h3	:= 0
        ram_data_h3  		:= 0
        h3_valid     		:= False
    }

    io_update_ram.update_ram_wr_valid   := h3_valid
    io_update_ram.update_ram_wr_addr    := update_ram_addr_h3
    io_update_ram.update_ram_wr_data    := ram_data_h3



}
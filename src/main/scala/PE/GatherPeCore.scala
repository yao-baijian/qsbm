package PE

import spinal.core._
import spinal.lib.fsm._

case class GatherPeCore(config:GatherPeCoreConfig) extends Component {
    val io = new Bundle {
        val switch_done = in Bool()
    }

    val io_state = new Bundle {
        val switch_done = in Bool()
        val pe_done = in Bool()
        val gather_pe_done = out Bool()
        val gather_pe_busy = out Bool()
        val need_gather    = out Bool()
    }
    val io_update_ram = new Bundle {
        val rd_addr_update_ram = out UInt (config.addr_width bits)
        val rd_en_update_ram = out Bool()
        val rd_data_update_ram = in Bits (config.data_width bits)
    }

    val io_vertex_ram = new Bundle {
        val rd_addr_vertex_ram = out UInt (6 bits)
        val rd_en_vertex_ram = out Bool()
        val rd_data_vertex_ram = out Bits (config.data_width bits)

        val wr_addr_vertex_ram = out UInt (6 bits)
        val wr_en_vertex_ram = out Bool()
        val wr_data_vertex_ram = out Bits (16 bits)
    }

    val gather_pe_fsm = new StateMachine {

        val IDLE        = new State with EntryPoint
        val OPERATE     = new State

        val operate     = Reg(Bool()) init( False )

        IDLE
        .whenIsActive(
            io_state.gather_pe_done := False
        )
        .whenIsActive (
            when (io_state.need_gather) {
                goto(OPERATE)
            }
        )

        OPERATE
        .onEntry(operate := True)
        .whenIsActive {
            when(h4_valid && ~h3_valid) {
                goto(IDLE)
            }
        }
        .onExit(io_state.gather_pe_done := True)
        .onExit(operate := False)
    }

val h1_valid = Reg(Bool()) init False
val h2_valid = Reg(Bool()) init False
val h3_valid = Reg(Bool()) init False
val h4_valid = Reg(Bool()) init False

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------
// TO DO
when ((gather_pe_fsm.operate) & (io_update_ram.rd_addr_update_ram =/= 63)) {
    io_update_ram.rd_addr_update_ram := io_update_ram.rd_addr_update_ram + 1
    io_vertex_ram.rd_addr_vertex_ram := io_vertex_ram.rd_addr_vertex_ram + 1
    h1_valid := True
} otherwise {
    io_update_ram.rd_addr_update_ram := 0
    io_vertex_ram.rd_addr_vertex_ram := 0
    h1_valid := False
}

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

val y_old_h2 = Reg(SInt(7 bits)) init 0
val y_new_h2 = Reg(SInt(7 bits)) init 0
val updated_value_h2 = Reg(SInt(16 bits)) init 0
val x_old_h2 = Reg(SInt(32 bits)) init 0
val alpha_h2 = Reg(SInt(32 bits)) init 0

when (h1_valid) {
    y_old_h2       := io_vertex_ram.rd_data_vertex_ram (7 downto 0)
    updated_value_h2 := io_update_ram.rd_data_update_ram
    x_old_h2       := io_vertex_ram.rd_data_vertex_ram (15 downto 8)
    h2_valid       := True
} otherwise {
    y_old_h2 := 0
    updated_value_h2 := 0
    x_old_h2 := 0
    h2_valid       := False
}

y_new_h2 := y_old_h2 + ((-32 + alpha_h2) * x_old_h2 + config.beta * updated_value_h2) * config.xi_dt

// -----------------------------------------------------------
// pipeline h3: x_new = x_old + y_new * dt
//              y_comp[np.abs(x_comp) > 1] = 0  
//              np.clip(x_comp,-1, 1)
// -----------------------------------------------------------

val y_new_h3 = Reg(SInt(32 bits)) init 0
val x_old_h3 = Reg(SInt(32 bits)) init 0
val x_new_h3 = Reg(SInt(32 bits)) init 0

when (h2_valid) {
    y_new_h3 := y_new_h2
    x_old_h3 := x_old_h2
    h3_valid := True
} otherwise {
    h3_valid := False
}

x_new_h3 := x_old_h3 + y_new_h2 * config.xi_dt

val x_new_cliped_h3 = SInt(8 bits)
val y_new_cliped_h3 = SInt(8 bits)

x_new_cliped_h3 := (x_new_h3 > config.positive_boundary) ? config.positive_boundary | ((x_new_h3 < config.negetive_boundary) ? config.negetive_boundary | x_new_h3 (7 downto 0))
y_new_cliped_h3 := ((x_new_h3 < config.positive_boundary ) && (x_new_h3 > config.negetive_boundary)) ? y_new_h3 | 0


// -----------------------------------------------------------
// pipeline h4: write back
// -----------------------------------------------------------

when (h3_valid) {
    io_vertex_ram.wr_data_vertex_ram := x_new_cliped_h3 ## y_new_cliped_h3
    io_vertex_ram.wr_addr_vertex_ram := io_vertex_ram.wr_addr_vertex_ram + 1
    h4_valid := True
} otherwise {
    io_vertex_ram.wr_data_vertex_ram := 0
    io_vertex_ram.wr_addr_vertex_ram := 0
    h4_valid := False
}
}


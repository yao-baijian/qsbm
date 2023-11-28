package PE

import spinal.core._
import spinal.lib.fsm._

import scala.language.postfixOps

case class GatherPeCore(config:GatherPeCoreConfig) extends Component {

    val io_state = new Bundle {
        val switch_done = in Bool()
        val gather_pe_done = in Bool()
        val gather_pe_busy = out Bool()
    }
    val io_update_ram = new Bundle {
        val rd_addr = out UInt (config.addr_width bits) setAsReg() init(0)
        val rd_data = in Bits (config.data_width bits)
    }

    val io_vertex_ram = new Bundle {
        val rd_addr = out UInt (6 bits) setAsReg() init(0)
        val rd_data = in Bits (config.data_width bits)
        val wr_addr = out UInt (6 bits) setAsReg() init(0)
        val wr_val = out Bool() setAsReg() init(False)
        val wr_data = out Bits (16 bits) setAsReg() init(0)
    }

    val h1_valid = Reg(Bool()) init False

    val h2_valid = Reg(Bool()) init False
    val y_old_h2 = Reg(SInt(7 bits)) init 0
    val y_new_h2 = Reg(SInt(7 bits)) init 0
    val updated_value_h2 = Reg(SInt(16 bits)) init 0
    val x_old_h2 = Reg(SInt(32 bits)) init 0
    val alpha_h2 = Reg(SInt(32 bits)) init 0

    val h3_valid = Reg(Bool()) init False
    val y_new_h3 = Reg(SInt(32 bits)) init 0
    val x_old_h3 = Reg(SInt(32 bits)) init 0
    val x_new_h3 = Reg(SInt(32 bits)) init 0
    val x_new_cliped_h3 = SInt(8 bits) init 0
    val y_new_cliped_h3 = SInt(8 bits) init 0

    val h4_valid = Reg(Bool()) init False

    val gather_pe_fsm = new StateMachine {

        val IDLE        = new State with EntryPoint
        val OPERATE     = new State

        IDLE
        .whenIsActive (
            when (io_state.switch_done) {
                io_state.gather_pe_done := False
                io_state.gather_pe_busy := True
                goto(OPERATE)
            }
        )

        OPERATE
        .whenIsActive {
            when (io_update_ram.rd_addr === 63) {
                io_state.gather_pe_busy := False
            }
            when(!h4_valid) {
                io_state.gather_pe_done := True
                goto(IDLE)
            }
        }
    }

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------
// TO DO

    when ((io_state.gather_pe_busy) & (io_update_ram.rd_addr =/= 63)) {
        io_update_ram.rd_addr := io_update_ram.rd_addr + 1
        io_vertex_ram.rd_addr := io_vertex_ram.rd_addr + 1
        h1_valid := True
    } otherwise {
        io_update_ram.rd_addr := 0
        io_vertex_ram.rd_addr := 0
        h1_valid := False
    }

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

    when (h1_valid) {
        y_old_h2       := io_vertex_ram.rd_data (7 downto 0).asSInt
        updated_value_h2 := io_update_ram.rd_data.asSInt
        x_old_h2       := io_vertex_ram.rd_data (15 downto 8).asSInt
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
    when (h2_valid) {
        y_new_h3 := y_new_h2
        x_old_h3 := x_old_h2
        h3_valid := True
    } otherwise {
        h3_valid := False
    }

    x_new_h3 := x_old_h3 + y_new_h2 * config.xi_dt
    x_new_cliped_h3 := (x_new_h3 > config.positive_boundary) ? config.positive_boundary | ((x_new_h3 < config.negetive_boundary) ? config.negetive_boundary | x_new_h3 (7 downto 0))
    y_new_cliped_h3 := ((x_new_h3 < config.positive_boundary ) && (x_new_h3 > config.negetive_boundary)) ? y_new_h3 | 0

// -----------------------------------------------------------
// pipeline h4: write back
// -----------------------------------------------------------

    when (h3_valid) {
        io_vertex_ram.wr_data := x_new_cliped_h3 ## y_new_cliped_h3
        io_vertex_ram.wr_addr := io_vertex_ram.wr_addr + 1
        io_vertex_ram.wr_val  := True
        h4_valid := True
    } otherwise {
        io_vertex_ram.wr_data := 0
        io_vertex_ram.wr_addr := 0
        io_vertex_ram.wr_val  := False
        h4_valid := False
    }
}


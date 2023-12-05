package PE

import spinal.core._
import spinal.lib.fsm._

import scala.language.postfixOps

case class GatherPeCore(config:GatherPeCoreConfig) extends Component {

    val io_state = new Bundle {
        val switch_done = in Bool()
        val gather_pe_done = out Bool()
        val gather_pe_busy = out Bool()
    }

    val io_update_ram = new Bundle {
        val rd_addr = out UInt (config.addr_width bits)
        val rd_data = in Bits (config.data_width bits)
    }

    val io_vertex_ram = new Bundle {
        val rd_addr = out UInt (6 bits)
        val rd_data = in Bits (config.data_width bits)

        val wr_addr = out UInt (6 bits)
        val wr_val = out Bool()
        val wr_data = out Bits (16 bits)
    }

    val alpha =  S(config.alpha, 8 bits)
    val beta  =  S(config.beta, 8 bits)
    val xi_dt =  S(config.xi_dt, 8 bits)
    val positive_boundary =  S(config.positive_boundary, 8 bits)
    val negetive_boundary =  S(config.negetive_boundary, 8 bits)

    val h1_valid = Reg(Bool()) init False
    val update_ram_rd_addr_h1 = Reg(UInt (config.addr_width bits)) init 0
    val vertex_ram_rd_addr_h1 = Reg(UInt (config.addr_width bits)) init 0

    val h2_valid = Reg(Bool()) init False
    val y_old_h2 = Reg(SInt(8 bits)) init 0
    val y_new_h2 = SInt(32 bits)
    val updated_value_h2 = Reg(SInt(16 bits)) init 0
    val x_old_h2 = Reg(SInt(8 bits)) init 0

    val h3_valid = Reg(Bool()) init False
    val y_new_h3 = Reg(SInt(32 bits)) init 0
    val x_old_h3 = Reg(SInt(8 bits)) init 0

    val x_new_h3 = SInt(32 bits)
    val x_new_cliped_h3 = SInt(8 bits)
    val y_new_cliped_h3 = SInt(8 bits)

    val h4_valid = Reg(Bool()) init False
    val vertex_ram_wr_data_h4 =  Reg(Bits (16 bits)) init 0
    val vertex_ram_wr_val_h4 = Reg(Bool()) init False
    val vertex_ram_wr_addr_h4 = Reg(UInt (6 bits)) init 0

    val gather_pe_done = Reg(Bool()) init True
    val gather_pe_busy = Reg(Bool()) init False

    io_state.gather_pe_done := gather_pe_done
    io_state.gather_pe_busy := gather_pe_busy

    val gather_pe_fsm = new StateMachine {

        val IDLE        = new State with EntryPoint
        val OPERATE     = new State

        IDLE
        .whenIsActive (
            when (io_state.switch_done) {
                gather_pe_done := False
                gather_pe_busy := True
                goto(OPERATE)
            }
        )

        OPERATE
        .whenIsActive {
            when (update_ram_rd_addr_h1 === 63) {
                gather_pe_busy := False
            }
            when(!h4_valid) {
                gather_pe_done := True
                goto(IDLE)
            }
        }
    }

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------
// TO DO

    when (gather_pe_busy & (update_ram_rd_addr_h1 =/= 63)) {
        when (h1_valid) {
            update_ram_rd_addr_h1 := update_ram_rd_addr_h1 + 1
            vertex_ram_rd_addr_h1 := vertex_ram_rd_addr_h1 + 1
        } otherwise {
            h1_valid := True
        }
    } otherwise {
        update_ram_rd_addr_h1 := 0
        vertex_ram_rd_addr_h1 := 0
        h1_valid := False
    }

    io_update_ram.rd_addr := update_ram_rd_addr_h1
    io_vertex_ram.rd_addr := vertex_ram_rd_addr_h1

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

    when (h1_valid) {
        y_old_h2        := io_vertex_ram.rd_data (7 downto 0).asSInt
        updated_value_h2:= io_update_ram.rd_data.asSInt
        x_old_h2        := io_vertex_ram.rd_data (15 downto 8).asSInt
        h2_valid        := True
    } otherwise {
        y_old_h2        := 0
        updated_value_h2:= 0
        x_old_h2        := 0
        h2_valid        := False
    }

    y_new_h2 := (y_old_h2 + ((-32 + alpha) * x_old_h2 + beta * updated_value_h2) * xi_dt)

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
        y_new_h3 := 0
        x_old_h3 := 0
        h3_valid := False
    }

    x_new_h3 := (x_old_h3 + y_new_h3 * xi_dt)(31 downto 0)
    x_new_cliped_h3 := (x_new_h3 > positive_boundary) ? positive_boundary | ((x_new_h3 < negetive_boundary) ? negetive_boundary | x_new_h3(7 downto 0))
    y_new_cliped_h3 := Mux(((x_new_h3 < positive_boundary ) & (x_new_h3 > negetive_boundary)), y_new_h3(7 downto 0) , S(0))

// -----------------------------------------------------------
// pipeline h4: write back
// -----------------------------------------------------------

    when (h3_valid) {
        vertex_ram_wr_data_h4 := x_new_cliped_h3 ## y_new_cliped_h3
        vertex_ram_wr_addr_h4 := io_vertex_ram.wr_addr + 1
        vertex_ram_wr_val_h4  := True
        h4_valid := True
    } otherwise {
        vertex_ram_wr_data_h4 := 0
        vertex_ram_wr_addr_h4 := 0
        vertex_ram_wr_val_h4  := False
        h4_valid := False
    }

    io_vertex_ram.wr_data := vertex_ram_wr_data_h4
    io_vertex_ram.wr_addr := vertex_ram_wr_addr_h4
    io_vertex_ram.wr_val  := vertex_ram_wr_val_h4
}


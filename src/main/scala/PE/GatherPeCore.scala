package PE

import spinal.core._
import spinal.lib.fsm._

import scala.language.postfixOps

case class GatherPeCore(config:PeConfig) extends Component {
  
    val io_state = new Bundle {
        val switch_done     = in Bool()
        val gather_pe_done  = out Bool()
        val gather_pe_busy  = out Bool()
    }

    val io_update = new Bundle {
        val rd_addr     = out UInt (config.addr_width bits)
        val rd_data     = in Bits (config.data_width bits)
    }

    val io_vertex_ram = new Bundle {
        val rd_addr     = out UInt (config.addr_width bits)
        val rd_data     = in Bits (config.data_width bits)
        val wr_addr     = out UInt (config.addr_width bits)
        val wr_data     = out Bits (config.data_width bits)
        val wr_val      = out Bool()
    }

    val alpha               =  S(config.alpha, config.quant_precision_8 bits)
    val beta                =  S(config.beta, config.quant_precision_8 bits)
    val xi_dt               =  S(config.xi_dt, config.quant_precision_8 bits)
    val positive_boundary   =  S(config.positive_boundary, config.quant_precision_8 bits)
    val negetive_boundary   =  S(config.negetive_boundary, config.quant_precision_8 bits)

    val h1_valid            = Reg(Bool())                               init False
    val update_ram_rd_addr_h1 = Reg(UInt (config.addr_width bits))      init 0
    val vertex_ram_rd_addr_h1 = Reg(UInt (config.addr_width bits))      init 0

    val h2_valid            = Reg(Bool())                               init False
    val y_old_h2            = Reg(SInt(config.xy_width bits))           init 0
    val updated_value_h2    = Reg(SInt(config.data_width bits))         init 0
    val x_old_h2            = Reg(SInt(config.xy_width bits))           init 0
    val y_temp_h2           = SInt(config.quant_precision_32 bits)

    val h3_valid            = Reg(Bool())                               init False
    val y_temp_h3           = SInt(config.quant_precision_32 bits)
    val y_old_h3            = Reg(SInt(config.xy_width bits))           init 0
    val y_new_h3            = SInt(config.quant_precision_32 bits)
    val x_old_h3            = Reg(SInt(config.xy_width bits))           init 0

    val h4_valid            = Reg(Bool())                               init False
    val y_new_h4            = Reg(SInt(config.quant_precision_32 bits)) init 0
    val x_old_h4            = Reg(SInt(config.xy_width bits))           init 0
    val x_new_h4            = SInt(config.quant_precision_32 bits)
    val x_new_cliped_h4     = SInt(config.xy_width bits)
    val y_new_cliped_h4     = SInt(config.xy_width bits)

    val h5_valid            = Reg(Bool())                               init False
    val vertex_ram_wr_data_h5 =  Reg(Bits (config.data_width bits))     init 0
    val vertex_ram_wr_val_h5 = Reg(Bool())                              init False
    val vertex_ram_wr_addr_h5 = Reg(UInt (config.addr_width bits))      init 0

    val gather_pe_done      = Reg(Bool())                               init True
    val gather_pe_busy      = Reg(Bool())                               init False

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
            when (update_ram_rd_addr_h1 === config.matrix_size - 1) {
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

    when (gather_pe_busy & (update_ram_rd_addr_h1 =/= config.matrix_size - 1)) {
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

    io_update.rd_addr := update_ram_rd_addr_h1
    io_vertex_ram.rd_addr := vertex_ram_rd_addr_h1

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

    when (h1_valid) {
        y_old_h2        := io_vertex_ram.rd_data (7 downto 0).asSInt
        updated_value_h2:= io_update.rd_data.asSInt
        x_old_h2        := io_vertex_ram.rd_data (15 downto 8).asSInt
        h2_valid        := True
    } otherwise {
        y_old_h2        := 0
        updated_value_h2:= 0
        x_old_h2        := 0
        h2_valid        := False
    }

    y_temp_h2 := (-32 + alpha) * x_old_h2 + beta * updated_value_h2

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

    when (h2_valid) {
        h3_valid    := True
        y_old_h3    := y_old_h2
        y_temp_h3   := y_temp_h2
        x_old_h3    := x_old_h2
    } otherwise {
        h3_valid    := False
        y_old_h3    := 0
        y_temp_h3   := 0
        x_old_h3    := 0
    }

    y_new_h3 := y_old_h3 + y_temp_h3 * xi_dt

// -----------------------------------------------------------
// pipeline h4: x_new = x_old + y_new * dt
//              y_comp[np.abs(x_comp) > 1] = 0  
//              np.clip(x_comp,-1, 1)
// -----------------------------------------------------------
    when (h3_valid) {
        y_new_h4 := y_new_h3
        x_old_h4 := x_old_h3
        h4_valid := True
    } otherwise {
        y_new_h4 := 0
        x_old_h4 := 0
        h4_valid := False
    }

    x_new_h4        := (x_old_h4 + y_new_h4 * xi_dt)(31 downto 0)
    x_new_cliped_h4 := (x_new_h4 > positive_boundary) ? positive_boundary | ((x_new_h4 < negetive_boundary) ? negetive_boundary | x_new_h4(7 downto 0))
    y_new_cliped_h4 := Mux(((x_new_h4 < positive_boundary ) & (x_new_h4 > negetive_boundary)), y_new_h4(7 downto 0) , S(0))

// -----------------------------------------------------------
// pipeline h5: write back
// -----------------------------------------------------------

    when (h4_valid) {
        vertex_ram_wr_data_h5   := x_new_cliped_h4 ## y_new_cliped_h4
        vertex_ram_wr_addr_h5   := io_vertex_ram.wr_addr + 1
        vertex_ram_wr_val_h5    := True
        h5_valid                := True
    } otherwise {
        vertex_ram_wr_data_h5   := 0
        vertex_ram_wr_addr_h5   := 0
        vertex_ram_wr_val_h5    := False
        h4_valid                := False
    }

    io_vertex_ram.wr_data := vertex_ram_wr_data_h5
    io_vertex_ram.wr_addr := vertex_ram_wr_addr_h5
    io_vertex_ram.wr_val  := vertex_ram_wr_val_h5
}


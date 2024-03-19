package PE

import spinal.core._
import spinal.lib.fsm._
import spinal.lib._

import scala.language.postfixOps

case class GatherPeCore(config:PeConfig) extends Component {
  
    val io_state = new Bundle {
        val switch_done     = in Bool()
        val gather_pe_done  = out Bool()
        val gather_pe_busy  = out Bool()
        val writeback_valid = out Bool()
        val writeback_payload = out Bits(config.axi_extend_width bits)
    }
    //  TODO address width is not fit with gather pe
    val io_update = new Bundle {
        val rd_addr     = out UInt (4 bits)
        val rd_data     = in Vec(Bits (31 bits), 32)
    }

    val io_vertex = new Bundle {
        val rd_addr     = out UInt (4 bits)
        val rd_data     = in  Bits (config.axi_extend_width bits)
    }

    val alpha               =  S(config.alpha, 4 bits)
    val beta                =  S(config.beta, 5 bits)
    val xi_dt               =  S(config.xi_dt, config.quant_precision_8 bits)
    val positive_boundary   =  S(config.positive_boundary, config.quant_precision_8 bits)
    val negetive_boundary   =  S(config.negetive_boundary, 4 bits)

    val h1_valid            = Reg(Bool()) init False
    val update_ram_rd_addr_h1 = Reg(UInt (4 bits))      init 0
    val vertex_ram_rd_addr_h1 = Reg(UInt (4 bits))      init 0

    val h2_valid            = Reg(Bool()) init False
    val spmm_h2             = Vec(Reg(SInt(31 bits)) init 0, 32)
    val y_old_h2            = Vec(Reg(SInt(config.xy_width bits)) init 0, 32)
    val x_old_h2            = Vec(Reg(SInt(config.xy_width bits)) init 0, 32)
    val y_new_h2            = Vec(SInt(32 bits), 32)

    val h3_valid            = Reg(Bool()) init False
    val y_new_h3            = Vec(Reg(SInt(config.quant_precision_32 bits)) init 0, 32)
    val x_old_h3            = Vec(Reg(SInt(config.xy_width bits)) init 0, 32)
    val x_new_h3            = Vec(SInt(config.quant_precision_32 bits), 32)
    val x_new_cliped_h3     = Vec(SInt(config.xy_width bits), 32)
    val y_new_cliped_h3     = Vec(SInt(config.xy_width bits), 32)

    val h4_valid            = Reg(Bool()) init False
    val vertex_wb_data_h4   = Reg(Bits (config.axi_extend_width bits)) init 0

    val gather_pe_done      = Reg(Bool())                               init True
    val gather_pe_busy      = Reg(Bool())                               init False

    io_state.gather_pe_done := gather_pe_done
    io_state.gather_pe_busy := gather_pe_busy

    val gather_pe_fsm = new StateMachine {

        val IDLE        = new State with EntryPoint
        val OPERATE     = new State

        IDLE
          .whenIsActive {
              when(io_state.switch_done) {
                  gather_pe_done := False
                  gather_pe_busy := True
                  h1_valid       := True
                  goto(OPERATE)
              }
          }
        OPERATE
          .whenIsActive {
            when (update_ram_rd_addr_h1 === 15) {
                gather_pe_busy := False
                h1_valid       := False
            }
            when(!gather_pe_busy && h4_valid === False) {
                gather_pe_done  := True
                goto(IDLE)
            }
         }
    }

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------

    when (h1_valid) {
        update_ram_rd_addr_h1 := update_ram_rd_addr_h1 + 1
        vertex_ram_rd_addr_h1 := vertex_ram_rd_addr_h1 + 1
    } otherwise {
        update_ram_rd_addr_h1 := 0
        vertex_ram_rd_addr_h1 := 0
    }

    io_update.rd_addr       := update_ram_rd_addr_h1
    io_vertex.rd_addr       := vertex_ram_rd_addr_h1

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

    when (h1_valid) {
        for (i <- 0 until 32) {
            spmm_h2(i)  := io_update.rd_data(i).asSInt
            x_old_h2(i) := io_vertex.rd_data(15 downto 8).asSInt
            y_old_h2(i) := io_vertex.rd_data(7 downto 0).asSInt
        }
        h2_valid := True
    } otherwise {
        for (i <- 0 until 32) {
            spmm_h2(i) := 0
            y_old_h2(i) := 0
            x_old_h2(i) := 0
        }
        h2_valid := False
    }
    for (i <- 0 until 32) {
        y_new_h2(i) := (((-32 + alpha) * x_old_h2 (i)+ beta * spmm_h2(i))* xi_dt + y_old_h2(i))(31 downto 0)
    }

    // -----------------------------------------------------------
    // pipeline h3: x_new = x_old + y_new * dt
    //              y_comp[np.abs(x_comp) > 1] = 0
    //              np.clip(x_comp,-1, 1)
    // -----------------------------------------------------------

    when(h2_valid) {
        h3_valid := True
        for (i <- 0 until 32) {
            y_new_h3(i) := y_new_h2(i)
            x_old_h3(i) := x_old_h2(i)
        }
    } otherwise {
        h3_valid := False
        for (i <- 0 until 32) {
            y_new_h3(i) := 0
            x_old_h3(i) := 0
        }
    }
    for (i <- 0 until 32) {
        x_new_h3(i) := (x_old_h3(i) + y_new_h3(i) * xi_dt)(31 downto 0)
        x_new_cliped_h3(i) := (x_new_h3(i) > positive_boundary) ? positive_boundary | ((x_new_h3(i) < negetive_boundary) ? negetive_boundary | x_new_h3(i)(7 downto 0))
        y_new_cliped_h3(i) := Mux(((x_new_h3(i) < positive_boundary) & (x_new_h3(i) > negetive_boundary)), y_new_h3(i)(7 downto 0), S(0))
    }
// -----------------------------------------------------------
// pipeline h5: write back
// -----------------------------------------------------------

    when (h3_valid) {
        h4_valid                := True
        for (i <- 0 until 32) {
            vertex_wb_data_h4((i+1)*16 - 1 downto i*16) := x_new_cliped_h3(i) ## y_new_cliped_h3(i)
        }
    } otherwise {
        h4_valid                := False
        vertex_wb_data_h4       := 0
    }

    io_state.writeback_payload  := vertex_wb_data_h4
    io_state.writeback_valid    := h4_valid

}


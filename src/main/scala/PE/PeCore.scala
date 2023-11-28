package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class PeCore(config: PeCoreConfig) extends Component {

    val io_state = new Bundle {
        val last_update = in Bool()
        val globalreg_done = in Bool()
        val switch_done = in Bool()

        val pe_busy = out Bool() setAsReg() init (False)
        val need_new_vertex = out Bool() setAsReg() init (False)
    }

    val io_edge_fifo = new Bundle {
        val edge_fifo_ready = out Bool() setAsReg() init(True)
        val edge_fifo_in = in Bits (config.edge_width bits)
        val edge_fifo_valid = in Bool()
    }

    val io_vertex_reg = new Bundle {
        val addr = out UInt (config.vertex_reg_addr_width bits)
        val data = in Bits (config.vertex_reg_data_width bits)
    }

    val io_update_ram = new Bundle {
        val wr_valid = out Bool()
        val wr_addr = out UInt (config.update_ram_addr_width bits)
        val wr_data = out Bits (32 bits)

        val rd_valid = out Bool()
        val rd_addr = out UInt (config.update_ram_addr_width bits)
        val rd_data = in Bits (32 bits)
    }

    val hazard_s1           = Reg(Bool())
    val edge_value_h1       = Reg(SInt(8 bits)) init(0)
    val update_ram_addr_h1  = Reg(UInt (10 bits)) init(0)
    val vertex_reg_addr_h1  = Reg(UInt (config.vertex_reg_addr_width bits)) init(0)
    val hazard_s1_h1        = Reg(Bool()) init(False)
    val h1_valid            = Reg(Bool()) init(False)

    val vertex_reg_data_h2  = Reg(SInt(32 bits)) init (0)
    val edge_value_h2       = Reg(SInt(8 bits)) init (0)
    val h2_valid            = Reg(Bool()) init (False)
    val updata_data_old_h2  = Reg(SInt(32 bits)) init (0)
    val update_ram_addr_h2  = Reg(UInt (6 bits)) init (0)
    val hazard_s1_h2        = Reg(Bool()) init (False)

    val updata_data_h2      = SInt(32 bits)

    val h3_valid            = Reg(Bool()) init False
    val ram_data_h3         = Reg(SInt(32 bits)) init 0
    val update_ram_addr_h3  = Reg(UInt (6 bits)) init 0


    val pe_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val WAIT_DONE = new State
        val PAUSE = new State

        IDLE
          .whenIsActive {
              when (io_state.globalreg_done ) {
                  io_state.need_new_vertex  := False
              }
              when (io_state.globalreg_done && io_edge_fifo.edge_fifo_valid === True) {
                  io_state.pe_busy := True
                  io_edge_fifo.edge_fifo_ready := True
                  goto(OPERATE)
              }
          }

        OPERATE
          .whenIsActive {
              when(io_edge_fifo.edge_fifo_in === 0) {
                  goto(WAIT_DONE)
              }
          }
        WAIT_DONE
          .onEntry{
              io_edge_fifo.edge_fifo_ready := False
          }
          .whenIsActive {
              when(h3_valid === False) {
                  when (io_state.last_update) {
                      io_edge_fifo.edge_fifo_ready := False
                      io_state.pe_busy := False
                      goto(PAUSE)
                  } otherwise{
                      io_state.need_new_vertex  := True
                      io_edge_fifo.edge_fifo_ready := True
                      goto(IDLE)
                  }
              }
          }
        PAUSE
          .whenIsActive {
              when(io_state.switch_done) {
                  io_edge_fifo.edge_fifo_ready := True
                  io_state.need_new_vertex  := True
                  goto(IDLE)
              }
          }
    }

//-----------------------------------------------------------
// pipeline h0
//-----------------------------------------------------------

// WRITE AFTER READ
    hazard_s1 := (io_edge_fifo.edge_fifo_in (21 downto 12).asUInt === update_ram_addr_h1) ? True | False

//-----------------------------------------------------------
// pipeline h1
//-----------------------------------------------------------
// reg

    when (io_edge_fifo.edge_fifo_ready && io_edge_fifo.edge_fifo_valid && io_edge_fifo.edge_fifo_in =/= 0) {
        vertex_reg_addr_h1  := io_edge_fifo.edge_fifo_in (15 downto 10).asUInt
        update_ram_addr_h1  := io_edge_fifo.edge_fifo_in (9 downto 4).asUInt
        edge_value_h1       := io_edge_fifo.edge_fifo_in (3 downto 0).asSInt
        h1_valid            := True
        hazard_s1_h1        := hazard_s1
    } otherwise {
        vertex_reg_addr_h1  := 0
        update_ram_addr_h1  := 0
        edge_value_h1       := 0
        hazard_s1_h1        := False
        h1_valid            := False
    }

    io_update_ram.rd_valid   := h1_valid
    io_update_ram.rd_addr    := update_ram_addr_h1
    io_vertex_reg.addr := vertex_reg_addr_h1


//-----------------------------------------------------------
// pipeline h2
//-----------------------------------------------------------

    when (h1_valid) {
        edge_value_h2  		:= edge_value_h1
        update_ram_addr_h2:= update_ram_addr_h1
        updata_data_old_h2:= io_update_ram.rd_data.asSInt
        vertex_reg_data_h2:= io_vertex_reg.data.asSInt
        hazard_s1_h2      := hazard_s1_h1
        h2_valid       		:= True
    } otherwise {
        edge_value_h2  		:= 0
        update_ram_addr_h2:= 0
        updata_data_old_h2:= 0
        vertex_reg_data_h2:= 0
        hazard_s1_h2      := False
        h2_valid       		:= False
    }

    updata_data_h2 := hazard_s1_h2 ? ram_data_h3 | updata_data_old_h2  + vertex_reg_data_h2 * edge_value_h2;

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

    io_update_ram.wr_valid   := h3_valid
    io_update_ram.wr_addr    := update_ram_addr_h3
    io_update_ram.wr_data    := ram_data_h3.asBits
}
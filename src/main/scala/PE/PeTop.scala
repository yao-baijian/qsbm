package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class PeTop(config:PETopConfig) extends Component {

    val io = new Bundle {
        val last_update         = in Vec(Bool(), config.core_num)
        val edge_stream         = Vec(Vec(slave Stream (Bits(128 / 8 bits)), config.thread_num), config.core_num)
        val vertex_stream       = Vec(slave Stream (Bits(128 bits)), config.core_num)
        val bundle_busy_table   = out Bits(4 bits)
        val vertex_stream_top   = slave Stream (Bits(128 bits))
        val writeback_stream    = Vec(master Stream (Bits(16 bits)), config.vertex_reg_num)
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val switch_done             = Reg(Bool()) init False
    val switch                  = Reg(Bool()) init True
    val vertex_reg_en           = Reg(Bits(config.vertex_reg_num bits)) init 1
    val vertex_reg_cnt          = Reg(UInt(3 bits)) init 0
    val bundle_busy             = Bool()
    val bundle_busy_table       = Reg(Bits(config.core_num bits))
    val last_update             = Bool()
    val need_update             = Reg(Bool()) init False
    val gather_pe_busy          = Bool()
    val gather_pe_busy_table    = Reg(Bits(config.gather_pe_num bits))
    val writeback_busy          = Reg(Bool()) init False
    val writeback_payload       = Vec(Reg(Bits(16 bits)), config.thread_num)
    val writeback_valid         = Reg(Bool())
    val writeback_pointer       = Reg(UInt(6 bits))
    val update_reg_srst         = Vec(Reg(Bool()),config.core_num)

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val pe_bundle_array             = new Array[PeBundle](config.core_num)
    val gather_pe_group             = new Array[GatherPeCore](config.gather_pe_num)
    val vertex_reg_group_A          = new Array[DualModeReg](config.thread_num)
    val vertex_reg_group_B          = new Array[DualModeReg](config.thread_num)
    val pe_bundle_update_reg_group  = Vec(Vec(Vec(Reg(Bits(config.reg_config.data_width bits)),config.reg_config.reg_depth), config.gather_pe_num),config.core_num)
    val update_reg_group            = Vec(Vec(Reg(Bits(config.reg_config.data_width bits)), config.reg_config.reg_depth), config.thread_num)

    //-----------------------------------------------------
    // Module Instantiation
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        pe_bundle_array(i) = PeBundle(config.pe_bundle_config)
        pe_bundle_array(i).setName("pe_bundle_" + i.toString)
    }

    for (i <- 0 until config.thread_num) {
        gather_pe_group(i) = GatherPeCore(config.gather_pe_bundle_config)
        gather_pe_group(i).setName("gather_pe_" + i.toString)
        vertex_reg_group_A(i) = DualModeReg(config.vertex_config)
        vertex_reg_group_A(i).setName("vertex_regA_" + i.toString)
        vertex_reg_group_B(i) = DualModeReg(config.vertex_config)
        vertex_reg_group_B(i).setName("vertex_regB_" + i.toString)
    }
    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        pe_bundle_array(i).io_global_reg.vertex_stream << io.vertex_stream(i)

        pe_bundle_array(i).io_state.last_update <> io.last_update(i)
        bundle_busy_table(i) := pe_bundle_array(i).io_state.bundle_busy
        pe_bundle_array(i).io_state.switch_done <> switch_done

        for (j <- 0 until config.thread_num) {
            pe_bundle_array(i).io_fifo.pe_fifo(j) << io.edge_stream(i)(j)

            when (pe_bundle_array(i).io_update_reg.wr_valid(j)) {
                pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.wr_addr(j)) := pe_bundle_array(i).io_update_reg.wr_data(j)
            }
            pe_bundle_array(i).io_update_reg.rd_data(j) := pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.rd_addr(j))
        }
        // Update reg need soft reset, after all update result is sum up in 1 update reg
        when (update_reg_srst(i)) {
            for (k <- 0 until config.thread_num) {
                for (l <- 0 until config.matrix_size)
                pe_bundle_update_reg_group(i)(k)(l) := 0
            }
        }
    }

    for (i <- 0 until config.gather_pe_num) {
        gather_pe_group(i).io_state.switch_done := switch_done
    }

    for (i <- 0 until config.vertex_reg_num) {
        vertex_reg_group_A(i).io.in_stream.valid := Mux(switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid,  False) , False)
        vertex_reg_group_A(i).io.in_stream.payload := Mux(switch, Mux(vertex_reg_en(i)=== True, io.vertex_stream_top.payload, B(0)), B(0))
        vertex_reg_group_B(i).io.in_stream.valid := Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid, False), False)
        vertex_reg_group_B(i).io.in_stream.payload := Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.payload, B(0)), B(0))

        vertex_reg_group_A(i).io_gather_pe.wr_valid := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_val , False )
        vertex_reg_group_A(i).io_gather_pe.wr_addr := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_addr , U(0) )
        vertex_reg_group_A(i).io_gather_pe.wr_data := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_data , B(0) )
        vertex_reg_group_A(i).io_gather_pe.rd_addr := Mux(!switch, Mux(writeback_busy, writeback_pointer ,gather_pe_group(i).io_vertex_ram.rd_addr), U(0))

        vertex_reg_group_B(i).io_gather_pe.wr_valid := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_val, False)
        vertex_reg_group_B(i).io_gather_pe.wr_addr := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_addr, U(0))
        vertex_reg_group_B(i).io_gather_pe.wr_data := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_data, B(0))
        vertex_reg_group_B(i).io_gather_pe.rd_addr := Mux(switch, Mux(writeback_busy, writeback_pointer ,gather_pe_group(i).io_vertex_ram.rd_addr), U(0))

        gather_pe_group(i).io_vertex_ram.rd_data   := Mux(!switch, Mux(writeback_busy, B(0) ,vertex_reg_group_A(i).io_gather_pe.rd_data), Mux(writeback_busy, B(0) ,vertex_reg_group_B(i).io_gather_pe.rd_data))

        vertex_reg_group_A(i).io.srst   := Mux(!switch, switch_done, False)
        vertex_reg_group_B(i).io.srst   := Mux(switch, switch_done, False)

        gather_pe_busy_table(i) := !gather_pe_group(0).io_state.gather_pe_done
    }

    val rdy_list_B = Bits(config.vertex_reg_num bits)
    val rdy_list_A = Bits(config.vertex_reg_num bits)

    rdy_list_A := 0
    rdy_list_B := 0

    for (i <- 0 until config.vertex_reg_num) {
        rdy_list_A(i) := vertex_reg_en(i) & vertex_reg_group_A(i).io.in_stream.ready
        rdy_list_B(i) := vertex_reg_en(i) & vertex_reg_group_B(i).io.in_stream.ready
    }

    io.vertex_stream_top.ready := Mux(switch, rdy_list_A(vertex_reg_cnt),  rdy_list_B(vertex_reg_cnt))

    //-----------------------------------------------------
    // Other Logic
    //-----------------------------------------------------

    // Todo : need found invalid here
    when(vertex_reg_en =/= 0 && io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0 ) {
        vertex_reg_cnt:= (vertex_reg_cnt + 1)(2 downto 0)
        vertex_reg_en := vertex_reg_en |<< 1
    } elsewhen (switch_done) {
        vertex_reg_cnt := 0
        vertex_reg_en := 0x00000001
    }

    // Todo this part is not capable of parameterlize
    when(need_update) {
        for (i <- 0 until config.thread_num) {
            for (j <- 0 until config.matrix_size) {
                update_reg_group (i)(j) := (pe_bundle_update_reg_group(0)(i)(j).asSInt + pe_bundle_update_reg_group(1)(i)(j).asSInt +
                                            pe_bundle_update_reg_group(2)(i)(j).asSInt + pe_bundle_update_reg_group(3)(i)(j).asSInt).asBits
            }
        }
    }

    for (i <- 0 until config.gather_pe_num) {
        gather_pe_group(i).io_update_ram.rd_data := update_reg_group(i)(gather_pe_group(i).io_update_ram.rd_addr)
    }

    io.bundle_busy_table := bundle_busy_table
    bundle_busy := bundle_busy_table.andR
    gather_pe_busy := gather_pe_busy_table.andR

    //Todo: this logic might be problematic
    last_update := io.last_update(0)

    //Todo: combinational busy signal need redo

    //-----------------------------------------------------
    // Write Back Control
    //-----------------------------------------------------

    when(writeback_busy && writeback_pointer =/= config.matrix_size - 1) {
        writeback_pointer   := writeback_pointer+1
        writeback_valid     := True
        for  (i <- 0 until config.vertex_reg_num) {
            writeback_payload(i) := Mux(switch, vertex_reg_group_B(i).io_gather_pe.rd_data, vertex_reg_group_A(i).io_gather_pe.rd_data)
        }
    } otherwise{
        writeback_pointer   := 0
        writeback_valid     := False
        for (i <- 0 until config.vertex_reg_num) {
            writeback_payload(i) := 0
        }
    }

    for (i <- 0 until config.vertex_reg_num) {
        io.writeback_stream(i).payload := writeback_payload(i)
        io.writeback_stream(i).valid   := writeback_valid
    }

    //-----------------------------------------------------
    // State Machine
    //-----------------------------------------------------

    val pe_fsm_top = new StateMachine {

        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val UPDATE_SUM_AND_SWITCH = new State

        IDLE
          .whenIsActive(
              when(bundle_busy) {
                  goto(OPERATE)
              }
          )
        OPERATE
          .whenIsActive {
              when(last_update & !bundle_busy & !writeback_busy) {
                  need_update := True
                  goto(UPDATE_SUM_AND_SWITCH)
              }
          }
        UPDATE_SUM_AND_SWITCH
          .whenIsActive {
              when(need_update) {
                  for (i <- 0 until config.core_num) {
                      update_reg_srst(i) := True
                  }
                  need_update := False
                  switch      := !switch
                  switch_done := True
              } otherwise {
                  for (i <- 0 until config.core_num) {
                      update_reg_srst(i) := False
                  }
                  switch_done := False
                  when (bundle_busy) {
                      goto(OPERATE)
                  } otherwise {
                      goto(IDLE)
                  }
              }
          }
    }

    val gather_pe_fsm_top = new StateMachine {

        val IDLE = new State with EntryPoint
        val GATHER_PE = new State
        val WRITE_BACK = new State

        IDLE
          .whenIsActive {
              when(switch_done) {
                  goto(GATHER_PE)
              }
          }

        GATHER_PE
          .whenIsActive {
              when(!gather_pe_busy) {
                  goto(WRITE_BACK)
                  writeback_busy := True
              }
          }
        WRITE_BACK
          .whenIsActive {
              when(writeback_pointer === config.matrix_size - 1) {
                  writeback_busy := False
                  goto(IDLE)
              }
          }
    }
}

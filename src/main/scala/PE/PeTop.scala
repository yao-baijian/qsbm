package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

// TO DO:
// connnect vertex stream
// connect update reg
// switch between pe bundle and gather pe
// connect gather pe 

case class PeTop() extends Component {

    val io = new Bundle {
        val last_update = in Vec(Bool(),4)
        val edge_stream = Vec(Vec(slave Stream (Bits(128 / 8 bits)), 8), 4)
        val vertex_stream = Vec(slave Stream (Bits(128 bits)), 4)

        val bundle_busy_table = out Bits(4 bits)
        val vertex_stream_top = slave Stream (Bits(128 bits))

        val writeback_stream = Vec(master Stream (Bits(16 bits)), 8)
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val vertex_config = VertexConfig()
    val reg_config = RegConfig()
    val pe_bundle_config = PeBundleConfig()
    val gather_pe_bundle_config = GatherPeCoreConfig()

    val switch_done = Reg(Bool()) init (False)
    val switch = Reg(Bool()) init (True)

    val vertex_reg_en = Reg(Bits(8 bits)) init (1)
    val vertex_reg_cnt = Reg(UInt(3 bits)) init (0)

    val bundle_busy = Bool()
    val bundle_busy_table = Reg(Bits(4 bits))
    val last_update = Bool()
    val need_update = Reg(Bool()) init (False)

    val gather_pe_busy = Bool()
    val gather_pe_busy_table = Reg(Bits(8 bits))
    val writeback_busy = Reg(Bool()) init(False)

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------
    val pe_bundle_array = new Array[PeBundle](4)
    val pe_bundle_update_reg_group = Vec(Vec(Vec(Reg(Bits(reg_config.data_width bits)),reg_config.reg_depth),8),4)
    val update_reg_srst = Vec(Reg(Bool()),4)
    val gather_pe_group = new Array[GatherPeCore](8)
    val update_reg_group = Vec(Vec(Reg(Bits(reg_config.data_width bits)), reg_config.reg_depth), 8)
    val vertex_reg_group_A = new Array[DualModeReg](8)
    val vertex_reg_group_B = new Array[DualModeReg](8)


    //-----------------------------------------------------
    // Module Instantiation
    //-----------------------------------------------------

    for (i <- 0 until 4) {
        pe_bundle_array(i) = PeBundle(pe_bundle_config)
        pe_bundle_array(i).setName("pe_bundle_" + i.toString)
    }

    for (i <- 0 until 8) {
        gather_pe_group(i) = GatherPeCore(gather_pe_bundle_config)
        gather_pe_group(i).setName("gather_pe_" + i.toString)

        vertex_reg_group_A(i) = DualModeReg(vertex_config)
        vertex_reg_group_A(i).setName("vertex_regA_" + i.toString)
        vertex_reg_group_B(i) = DualModeReg(vertex_config)
        vertex_reg_group_B(i).setName("vertex_regB_" + i.toString)
    }

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    for (i <- 0 until 4) {
        pe_bundle_array(i).io_global_reg.vertex_stream << io.vertex_stream(i)

        pe_bundle_array(i).io_state.last_update <> io.last_update(i)
        bundle_busy_table(i) := pe_bundle_array(i).io_state.bundle_busy
        pe_bundle_array(i).io_state.switch_done <> switch_done

        for (j <- 0 until 8) {
            pe_bundle_array(i).io_fifo.pe_fifo(j) << io.edge_stream(i)(j)

            when (pe_bundle_array(i).io_update_reg.wr_valid(j)) {
                pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.wr_addr(j)) := pe_bundle_array(i).io_update_reg.wr_data(j)
            }
            pe_bundle_array(i).io_update_reg.rd_data(j) := pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.rd_addr(j))
        }
        // Update reg need soft reset, after all update result is sum up in 1 update reg
        when (update_reg_srst(i)) {
            for (k <- 0 until 8) {
                for (l <- 0 until 64)
                pe_bundle_update_reg_group(i)(k)(l) := 0
            }
        }
    }

    for (i <- 0 until 8) {
        gather_pe_group(i).io_state.switch_done := switch_done
    }

    for (i <- 0 until 8) {
        vertex_reg_group_A(i).io.in_stream.valid := Mux(switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid,  False) , False)
        vertex_reg_group_A(i).io.in_stream.payload := Mux(switch, Mux(vertex_reg_en(i)=== True, io.vertex_stream_top.payload, B(0)), B(0))
        vertex_reg_group_B(i).io.in_stream.valid := Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid, False), False)
        vertex_reg_group_B(i).io.in_stream.payload := Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.payload, B(0)), B(0))

        vertex_reg_group_A(i).io_gather_pe.wr_valid := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_val , False )
        vertex_reg_group_A(i).io_gather_pe.wr_addr := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_addr , U(0) )
        vertex_reg_group_A(i).io_gather_pe.wr_data := Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_data , B(0) )
        vertex_reg_group_A(i).io_gather_pe.rd_addr := Mux(!switch, Mux(writeback_busy, writeback_pointer ,gather_pe_group(i).io_vertex_ram.rd_addr), U(0))
        vertex_reg_group_A(i).io_gather_pe.rd_data := Mux(!switch, Mux(writeback_busy, writeback_payload ,gather_pe_group(i).io_vertex_ram.rd_data）, B(0))

        vertex_reg_group_B(i).io_gather_pe.wr_valid := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_val, False)
        vertex_reg_group_B(i).io_gather_pe.wr_addr := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_addr, U(0))
        vertex_reg_group_B(i).io_gather_pe.wr_data := Mux(switch, gather_pe_group(i).io_vertex_ram.wr_data, B(0))
        vertex_reg_group_B(i).io_gather_pe.rd_addr := Mux(switch, gather_pe_group(i).io_vertex_ram.rd_addr, U(0))
        vertex_reg_group_B(i).io_gather_pe.rd_data := Mux(switch, gather_pe_group(i).io_vertex_ram.rd_data, B(0))

        vertex_reg_group_A(i).io.srst   := Mux(!switch, switch_done, False)
        vertex_reg_group_B(i).io.srst   := Mux(switch, switch_done, False)

        gather_pe_busy_table(i) := !gather_pe_group(0).io_state.gather_pe_done
    }

    val rdy_list_B = Reg(Bits(8 bits)) init(0)
    val rdy_list_A = Reg(Bits(8 bits)) init(0)
    for (i <- 0 until 8) {
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

    when(need_update) {
        for (i <- 0 until 8) {
            for (j <- 0 until 64) {
                update_reg_group (i)(j) := (pe_bundle_update_reg_group(0)(i)(j).asSInt + pe_bundle_update_reg_group(1)(i)(j).asSInt +
                                            pe_bundle_update_reg_group(2)(i)(j).asSInt + pe_bundle_update_reg_group(3)(i)(j).asSInt).asBits
            }
        }
    }

    for (i <- 0 until 8) {
        gather_pe_group(i).io_update_ram.rd_data := update_reg_group(i)(gather_pe_group(i).io_update_ram.rd_addr)
    }

    io.bundle_busy_table := bundle_busy_table
    bundle_busy := bundle_busy_table.andR
    gather_pe_busy := gather_pe_busy_table.andR

    //Todo: this logic might be problematic
    last_update := io.last_update(0)

    //Todo: combinational busy signal need redo

    //-----------------------------------------------------
    // Write Back
    //-----------------------------------------------------

//    val writeback_stream_payload
//    val writeback_stream_valid

    val writeback_payload = Bits(16 bits)
    val writeback_pointer = Reg(Bits(7 bits))
    val writeback_done = Reg(Bool())
    when(writeback_busy && writeback_pointer =/= 64) {
        for  (i <- 0 until 8) {
            vertex_reg_group_A(i).
        }

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
                  need_update := False
                  switch      := !switch
                  switch_done := True
              } otherwise {
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
              when(writeback_done) {
                  writeback_busy := False
                  goto(IDLE)
              }
          }
    }
}

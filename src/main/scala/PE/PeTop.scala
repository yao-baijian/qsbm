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
        val vertex_stream = Vec(slave Stream (Bits(128 / 8 bits)), 4)

        val bundle_busy = out Vec(Bool(),4)
        val vertex_stream_top = slave Stream (Bits(128 / 8 bits))
    }

    val reg_config = RegConfig()
    val pe_bundle_config = PeBundleConfig()
    val gather_pe_bundle_config = GatherPeCoreConfig()

    val switch_done = Reg(Bool()) init (False)
    val switch = Reg(Bool()) init (True)

    val vertex_counter = Reg(UInt(3 bits)) init (0)
    val vertex_counter_en = Reg(Bool()) init (True)
    val vertex_reg_en = Reg(Bits(8 bits)) init (1)

    val bundle_busy = Bool()
    val last_update = Bool()
    val need_update = Reg(Bool()) init (False)

    val gather_pe_busy = Bool()
    val writeback_busy = Reg(Bool()) init(False)

    val pe_bundle_array = new Array[PeBundle](4)
    val pe_bundle_update_reg_group = new Array[Array[Vec[Bits]]](4)
//  val pe_bundle_reg_group =  Array.tabulate(4)(_ => Vec)
//  alternative declaration of pe_bundle_reg_group

//  PE Bundle, each with respective update reg group
    val update_reg_srst = new Array[Bool](4)

    for (i <- 0 until 3) {
        pe_bundle_array(i) = PeBundle(pe_bundle_config)
        pe_bundle_array(i).setName("pe_bundle" + i.toString)
        pe_bundle_array(i).io_global_reg.vertex_stream <> io.vertex_stream(i)
        pe_bundle_array(i).io_state.last_update <> io.last_update(i)
        pe_bundle_array(i).io_state.bundle_busy <> io.bundle_busy(i)

        pe_bundle_array(i).io_state.switch_done <> switch_done

        for (j <- 0 until 7) {
            pe_bundle_array(i).io_fifo.pe_fifo(j) <> io.edge_stream(i)(j)
            pe_bundle_update_reg_group(i)(j) = Vec(Reg(Bits(reg_config.data_width bits)) init(0), reg_config.reg_depth)
            pe_bundle_update_reg_group(i)(j).setName("pe_bundle"+ i.toString+"_reg"+j.toString)

            when (pe_bundle_array(i).io_update_reg.update_reg_wr_valid(j)) {
                pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.update_reg_wr_addr(j)) := pe_bundle_array(i).io_update_reg.update_reg_wr_data(j)
            }
            pe_bundle_array(i).io_update_reg.update_reg_rd_data(j) := pe_bundle_update_reg_group(i)(j)(pe_bundle_array(i).io_update_reg.update_reg_rd_addr(j))
        }
        // Update reg need soft reset, after all update result is sum up in 1 update reg
        when (update_reg_srst(i)) {
            for (k <- 0 until 7) {
                for (l <- 0 until 63)
                pe_bundle_update_reg_group(i)(k)(l) := 0
            }
        }
    }

    val gather_pe_group = new Array[GatherPeCore](8)
    for (i <- 0 until 7) {
        gather_pe_group(i) = GatherPeCore(gather_pe_bundle_config)
        gather_pe_group(i).setName("gather_pe" + i.toString)
    }

    val update_reg_group = new Array[Vec[Bits]](8)

    for (i <- 0 until 7) {
        update_reg_group(i) = Vec(Reg(Bits(reg_config.data_width bits)) init (0), reg_config.reg_depth)
        update_reg_group(i).setName("update_reg" + i.toString)
    }

    val vertex_reg_group_A = new Array[DualModeReg](8)
    val vertex_reg_group_B = new Array[DualModeReg](8)

    for (i <- 0 until 7) {
        vertex_reg_group_A(i) = DualModeReg(reg_config)
        vertex_reg_group_A(i).setName("vertex_regA_" + i.toString)
        vertex_reg_group_B(i) = DualModeReg(reg_config)
        vertex_reg_group_B(i).setName("vertex_regB_" + i.toString)

        io.vertex_stream_top.ready <> Mux(switch, Mux( vertex_reg_en(i) === True, vertex_reg_group_A(i).io.in_stream.ready, False), False)
        vertex_reg_group_A(i).io.in_stream.valid <> Mux(switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid,  False) , False)
        vertex_reg_group_A(i).io.in_stream.payload <> Mux(switch, Mux(vertex_reg_en(i)=== True, io.vertex_stream_top.payload, 0), 0)

        io.vertex_stream_top.ready <> Mux(!switch, Mux(vertex_reg_en(i) === True, vertex_reg_group_B(i).io.in_stream.ready, False), False)
        vertex_reg_group_B(i).io.in_stream.valid <> Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid, False), False)
        vertex_reg_group_B(i).io.in_stream.payload <> Mux(!switch, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.payload, 0), 0)

        vertex_reg_group_A(i).io_gather_pe.wr_valid <> Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_val , False )
        vertex_reg_group_A(i).io_gather_pe.wr_addr <> Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_addr , 0 )
        vertex_reg_group_A(i).io_gather_pe.wr_data <> Mux(!switch , gather_pe_group(i).io_vertex_ram.wr_data , 0 )

        vertex_reg_group_B(i).io_gather_pe.wr_valid <> Mux(switch, gather_pe_group(i).io_vertex_ram.wr_val, False)
        vertex_reg_group_B(i).io_gather_pe.wr_addr <> Mux(switch, gather_pe_group(i).io_vertex_ram.wr_addr, 0)
        vertex_reg_group_B(i).io_gather_pe.wr_data <> Mux(switch, gather_pe_group(i).io_vertex_ram.wr_data, 0)

        vertex_reg_group_A(i).io.srst   <> Mux(!switch, switch_done, False)
        vertex_reg_group_B(i).io.srst   <> Mux(switch, switch_done, False)
    }

    when(vertex_counter > 7) {
        vertex_counter := 0
    } elsewhen (io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0 && vertex_counter_en) {
        vertex_counter := vertex_counter + 1
        vertex_reg_en  := vertex_reg_en << 1
    }

    when(vertex_counter > 7) {
        vertex_reg_en := vertex_reg_en << 1
    } elsewhen (switch_done) {
        vertex_reg_en := 0x00000001
    }

    //  Update reg group
    //  sum up 4 large pe result

    when(need_update) {
        for (i <- 0 until 7) {
            for (j <- 0 until 63) {
                update_reg_group (i)(j) := (pe_bundle_update_reg_group(1)(i)(j).asSInt + pe_bundle_update_reg_group(2)(i)(j).asSInt +
                                            pe_bundle_update_reg_group(3)(i)(j).asSInt + pe_bundle_update_reg_group(4)(i)(j).asSInt).asBits
            }
        }
    }

    for (i <- 0 until 7) {
        gather_pe_group(i).io_update_ram.rd_data := update_reg_group(i)(gather_pe_group(i).io_update_ram.rd_addr)
    }

    bundle_busy := pe_bundle_array(0).io_state.bundle_busy | pe_bundle_array(1).io_state.bundle_busy |
      pe_bundle_array(2).io_state.bundle_busy | pe_bundle_array(3).io_state.bundle_busy

    gather_pe_busy := !gather_pe_group(0).io_state.gather_pe_done & !gather_pe_group(1).io_state.gather_pe_done &
      !gather_pe_group(2).io_state.gather_pe_done & !gather_pe_group(3).io_state.gather_pe_done &
      !gather_pe_group(4).io_state.gather_pe_done & !gather_pe_group(5).io_state.gather_pe_done &
      !gather_pe_group(6).io_state.gather_pe_done & !gather_pe_group(7).io_state.gather_pe_done

    //Todo: this logic might be problematic
    last_update := io.last_update(0)


    //Todo: final write back

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
              when(!writeback_busy) {
                  goto(IDLE)
              }
          }
    }
}

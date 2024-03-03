/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-24 12:10:55
 * @LastEditors:  
 * @LastEditTime: 2023-12-09 10:35:39
 * @FilePath: \sboom\src\main\scala\PE\PeTop.scala
 * @Description: Parameterized PE top module:
    *               1. PE bundle array x4:  each PE bundle contains 8 PE and 8 FIFO
    *               2. Gather PE array x1:  contains 8 gather PE core
    *               3. Vertex register array: contains 8 vertex register
    *               4. Update register array: contains 8 update register
    *               5. Write back control: control the write back process
    *  the number of PE bundle, gather PE, vertex register, update register can be changed through config file  
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */


// Todo, interface and corresponding apply method need to be added

package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class PeTop(config:PeConfig) extends Component {

    val io = new Bundle {
        val last_update         = in Vec(Bool(), config.core_num) //big line ending
        val raw_edge_stream     = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val tag_stream          = Vec(slave Stream (Bits(config.tag_extend_width bits)), config.core_num) //edge index port
        val vertex_stream       = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val pe_rdy_table        = out Vec(Bool(), config.core_num)
        val bundle_busy_table   = out Vec(Bool(), config.core_num)
        val vertex_stream_top   = slave Stream (Bits(config.axi_extend_width bits))
        val writeback_stream    = Vec(master Stream (Bits(config.data_width bits)), config.thread_num)
        val srst                = in Bool()
        val bundle_sel          = in Vec(Bool(), config.core_num)
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val switch_done             = Reg(Bool())                       init False
    val switch                  = Reg(Bool())                       init True
    val vertex_reg_en           = Reg(Bits(config.thread_num bits)) init 1
    val vertex_reg_cnt          = Reg(UInt(6 bits))          init 0
    val bundle_busy             = Bool()
    val bundle_busy_table       = Vec(Bool(), config.core_num)
    val last_update             = Reg(Bool())                       init False
    val need_update             = Reg(Bool())                       init False
    val gather_pe_busy          = Bool()
    val gather_pe_busy_table    = Reg(Bits(config.thread_num bits))
    val writeback_busy          = Reg(Bool()) init False
    val writeback_payload       = Vec(Reg(Bits(16 bits)), config.thread_num)
    val writeback_valid         = Reg(Bool())
    val writeback_pointer       = Reg(UInt(6 bits))
    val update_reg_srst         = Vec(Reg(Bool()),config.core_num)
    val pe_rdy_table_all        = Vec(Vec(Bool(), config.thread_num),config.core_num)
    val all_zero                = Vec(Bool(), config.core_num)
    val all_zero_table          = Vec(Vec(Bool(), config.thread_num), config.core_num)

    val Config                  = PeConfig()

    //-----------------------------------------------------
    // Module Declaration & Instantiation
    //-----------------------------------------------------
    val high_to_low_converter       = HighToLowConvert(Config)
    val pe_bundle_array             = new Array[PeBundle](config.core_num)
    val gather_pe_group             = new Array[GatherPeCore](config.thread_num)
    val vertex_reg_group_A          = new Array[DualModeReg](config.thread_num)
    val vertex_reg_group_B          = new Array[DualModeReg](config.thread_num)
    val pe_core_update_reg          = Vec(Vec(Reg(Bits(config.data_width bits)),config.matrix_size * config.thread_num), config.core_num)
    val update_reg_group            = Vec(Reg(Bits(config.data_width bits)),config.matrix_size * config.thread_num)
    val pe_bundle_wire              = Vec(Vec(SInt(config.data_width bits),config.matrix_size * config.thread_num), config.core_num / 2 )

    // Fix update reg and vertex reg size
    for (i <- 0 until config.core_num) {
        pe_bundle_array(i) = PeBundle(Config)
        pe_bundle_array(i).setName("pe_bundle_" + i.toString)
        for (j <- 0 until config.thread_num) {
            pe_core_update_reg(i).foreach(_ init 0)
        }
        update_reg_group.foreach(_ init 0)
    }

    for (i <- 0 until config.thread_num) {
        gather_pe_group(i) = GatherPeCore(Config)
        gather_pe_group(i).setName("gather_pe_" + i.toString)
        vertex_reg_group_A(i) = DualModeReg(Config)
        vertex_reg_group_A(i).setName("vertex_regA_" + i.toString)
        vertex_reg_group_B(i) = DualModeReg(Config)
        vertex_reg_group_B(i).setName("vertex_regB_" + i.toString)
    }
    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------
    for (i <- 0 until config.core_num) {
        pe_bundle_array(i).io_global_reg.vertex_stream << io.vertex_stream(i)
        pe_bundle_array(i).io_state.last_update     <> io.last_update(i)
        bundle_busy_table(i) := pe_bundle_array(i).io_state.bundle_busy
        high_to_low_converter.io.in_edge_stream(i)  <> io.raw_edge_stream(i)
        high_to_low_converter.io.in_tag_stream(i)   <> io.tag_stream(i)

        for (j <- 0 until config.thread_num) {
            pe_rdy_table_all(i)(j) := pe_bundle_array(i).io_fifo.pe_fifo(j).ready
            all_zero_table (i)(j) := (high_to_low_converter.io.out_edge_stream(i)(j).payload === 0) & high_to_low_converter.io.out_edge_stream(i)(j).valid
        }

        io.pe_rdy_table(i) :=  pe_rdy_table_all(i).orR
        pe_bundle_array(i).io_state.switch_done <> switch_done
        all_zero(i) := all_zero_table (i).andR
        pe_bundle_array(i).io_state.all_zero := all_zero(i)

        for (j <- 0 until config.thread_num) {
            pe_bundle_array(i).io_fifo.pe_fifo(j)       <> high_to_low_converter.io.out_edge_stream(i)(j)
            pe_bundle_array(i).io_fifo.pe_tag(j)        <> high_to_low_converter.io.out_tag_stream(i)(j)
            pe_bundle_array(i).io_update_reg.rd_data(j) <> pe_core_update_reg(i)(pe_bundle_array(i).io_update_reg.rd_addr(j))
            when(update_reg_srst(i)) {
                for (k <- 0 until config.matrix_size ) {
                    pe_core_update_reg(i)(j * config.matrix_size + k) := 0
                }
            } elsewhen (pe_bundle_array(i).io_update_reg.wr_valid(j)) {
                pe_core_update_reg(i)(pe_bundle_array(i).io_update_reg.wr_addr(j)) := pe_bundle_array(i).io_update_reg.wr_data(j)
            }
        }
    }

    for (i <- 0 until config.thread_num) {
        gather_pe_group(i).io_state.switch_done := switch_done
    }

    for (i <- 0 until config.thread_num) {
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

    val rdy_list_B = Bits(config.thread_num bits)
    val rdy_list_A = Bits(config.thread_num bits)

    rdy_list_A := 0
    rdy_list_B := 0

    for (i <- 0 until config.thread_num) {
        rdy_list_A(i) := vertex_reg_en(i) & vertex_reg_group_A(i).io.in_stream.ready
        rdy_list_B(i) := vertex_reg_en(i) & vertex_reg_group_B(i).io.in_stream.ready
    }

    val vertex_reg_sel        = Reg(UInt(3 bits)) init 0
    io.vertex_stream_top.ready := Mux(switch, rdy_list_A(vertex_reg_sel),  rdy_list_B(vertex_reg_sel))

    //-----------------------------------------------------
    // Other Logic
    //-----------------------------------------------------

//    TODO this only support size 64 * 32 = 2048
    val vertex_reg_upperbound = Reg(UInt(6 bits)) init 7
    val vertex_reg_lowerbound = Reg(UInt(6 bits)) init 0


    when (switch_done) {
        vertex_reg_upperbound := vertex_reg_upperbound + 8
        vertex_reg_lowerbound := vertex_reg_lowerbound + 8
    } elsewhen (io.srst) {
        vertex_reg_upperbound := 7
        vertex_reg_lowerbound := 0
    }

    when(io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0) {
        vertex_reg_cnt  := vertex_reg_cnt + 1
    } elsewhen (switch_done || io.srst) {
        vertex_reg_cnt  := 0
    }

    when (vertex_reg_cnt === vertex_reg_lowerbound) {
        vertex_reg_en   := 0x00000001
        vertex_reg_sel  := 0
    } elsewhen (io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0 && vertex_reg_en =/= 0) {
        vertex_reg_en   := vertex_reg_en |<< 1
        vertex_reg_sel  := vertex_reg_sel + 1
    }


    // Todo this part is not capable of parameterize
    for (i <- 0 until config.thread_num * config.matrix_size) {
        pe_bundle_wire(0)(i) := 0
        pe_bundle_wire(1)(i) := 0
    }

    when(need_update) {
        for (i <- 0 until config.thread_num * config.matrix_size) {
            pe_bundle_wire(0)(i) := pe_core_update_reg(0)(i).asSInt + pe_core_update_reg(1)(i).asSInt
            pe_bundle_wire(1)(i) := pe_core_update_reg(2)(i).asSInt + pe_core_update_reg(3)(i).asSInt
            update_reg_group (i) := (pe_bundle_wire(0)(i) + pe_bundle_wire(1)(i)).asBits
        }
    }

    val addr_mapper = Vec(UInt(9 bits), config.thread_num)
    for (i <- 0 until config.thread_num) {
        addr_mapper(i) := i*64
    }
    for (i <- 0 until config.thread_num) {
        gather_pe_group(i).io_update.rd_data := update_reg_group(addr_mapper(i) + gather_pe_group(i).io_update.rd_addr)
    }

    io.bundle_busy_table <> bundle_busy_table
    bundle_busy := bundle_busy_table.orR
    gather_pe_busy := gather_pe_busy_table.andR

    //-----------------------------------------------------
    // Write Back Control
    //-----------------------------------------------------

    when(writeback_busy && writeback_pointer =/= config.matrix_size - 1) {
        writeback_pointer   := writeback_pointer + 1
        writeback_valid     := True
        for  (i <- 0 until config.thread_num) {
            writeback_payload(i) := Mux(switch, vertex_reg_group_B(i).io_gather_pe.rd_data, vertex_reg_group_A(i).io_gather_pe.rd_data)
        }
    } otherwise{
        writeback_pointer   := 0
        writeback_valid     := False
        for (i <- 0 until config.thread_num) {
            writeback_payload(i) := 0
        }
    }

    for (i <- 0 until config.thread_num) {
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
              when (!last_update) {
                  last_update := io.last_update(0)
              }
              when(last_update & !bundle_busy & !writeback_busy) {
                  need_update := True
                  goto(UPDATE_SUM_AND_SWITCH)
              }
          }
        UPDATE_SUM_AND_SWITCH
          .whenIsActive {
              when(last_update) {
                  last_update := False
              }
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

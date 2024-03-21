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
        val writeback_valid     = out Bool()
        val writeback_payload   = out Bits(config.axi_extend_width bits)
        val srst                = in Bool()
        val bundle_sel          = in Vec(Bool(), config.core_num)
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val swap_done             = Reg(Bool())                       init False
    val swap                  = Reg(Bool())                       init True
    val vertex_reg_en           = Reg(Bits(config.thread_num bits)) init 1
    val vertex_reg_cnt          = Reg(UInt(6 bits))          init 0
    val bundle_busy             = Bool()
    val bundle_busy_table       = Vec(Bool(), config.core_num)
    val last_update             = Reg(Bool())                       init False
    val need_update             = Reg(Bool())                       init False
    val gather_pe_busy          = Bool()
    val update_reg_srst         = Vec(Reg(Bool()),config.core_num)
    val pe_rdy_table_all        = Vec(Vec(Bool(), config.thread_num),config.core_num)
    val all_zero                = Vec(Bool(), config.core_num)
    val all_zero_table          = Vec(Vec(Bool(), config.thread_num), config.core_num)
    val Config                  = PeConfig()

    //-----------------------------------------------------
    // Module Declaration & Instantiation
    //-----------------------------------------------------
    val high_to_low_converter       = HighToLowConvert(Config)
    val gather_pe_core              = GatherPeCore(Config)
    val pecore_array                = new Array[PeCore](config.core_num)
    val vertex_reg_group_A          = new Array[DualModeReg](config.thread_num)
    val vertex_reg_group_B          = new Array[DualModeReg](config.thread_num)
    val update_mem                  = new Array[Mem[Bits]](config.thread_num)
    val pe_core_update_reg          = Vec(Vec(Vec(Reg(Bits(31 bits)) init 0, config.matrix_size) ,config.thread_num), config.core_num)
    val pe_bundle_wire              = Vec(Vec(SInt(31 bits),config.core_num), config.thread_num)
    val pe_update_value             = Vec(SInt(31 bits), config.thread_num)

    // Fix update reg and vertex reg size
    for (i <- 0 until config.core_num) {
        pecore_array(i) = PeCore(Config)
        pecore_array(i).setName("pe_bundle_" + i.toString)
    }

    for (i <- 0 until config.thread_num) {
        update_mem(i) = Mem(Bits(31 bits), wordCount = config.matrix_size)
        vertex_reg_group_A(i) = DualModeReg(Config)
        vertex_reg_group_A(i).setName("vertex_regA_" + i.toString)
        vertex_reg_group_B(i) = DualModeReg(Config)
        vertex_reg_group_B(i).setName("vertex_regB_" + i.toString)
    }
    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    val pe_core_write_vec = Vec(Vec(Vec(UInt(8 bits), 64), 8 ), 4)

    for (i <- 0 until config.core_num) {
        pecore_array(i).io_global_reg.vertex_stream << io.vertex_stream(i)
        pecore_array(i).io_state.last_update <> io.last_update(i)
        bundle_busy_table(i) := pecore_array(i).io_state.pe_busy
        high_to_low_converter.io.in_edge_stream(i) <> io.raw_edge_stream(i)
        high_to_low_converter.io.in_tag_stream(i) <> io.tag_stream(i)

        for (j <- 0 until config.thread_num) {
            pe_rdy_table_all(i)(j) := pecore_array(i).io_fifo.pe_fifo(j).ready
            all_zero_table(i)(j) := (high_to_low_converter.io.out_edge_stream(i)(j).payload === 0) & high_to_low_converter.io.out_edge_stream(i)(j).valid
        }

        io.pe_rdy_table(i) := pe_rdy_table_all(i).orR
        pecore_array(i).io_state.switch_done <> swap_done
        all_zero(i) := all_zero_table(i).andR
        pecore_array(i).io_state.all_zero := all_zero(i)

        for (j <- 0 until config.thread_num) {
            pecore_array(i).io_fifo.pe_fifo(j) <> high_to_low_converter.io.out_edge_stream(i)(j)
            pecore_array(i).io_fifo.pe_tag(j) <> high_to_low_converter.io.out_tag_stream(i)(j)
            pecore_array(i).io_update.rd_data(j) := pe_core_update_reg(i).read(pecore_array(i).io_update.rd_addr(j)(8 downto 6))(pecore_array(i).io_update.rd_addr(j)(5 downto 0))

            for (k <- 0 until config.matrix_size) {
                for (l <- 0 until config.thread_num) {
                    when (update_reg_srst(i)) {
                        pe_core_write_vec(i)(j)(k)(l) := True
                    } otherwise {
                        pe_core_write_vec(i)(j)(k)(l) := (pecore_array(i).io_update.wr_addr(l) === j * config.matrix_size + k) && pecore_array(i).io_update.wr_valid(i)
                    }
                }

                switch(pe_core_write_vec(i)(j)(k)) {
                    is(1) (
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(0).asBits
                    )
                    is(2)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(1).asBits
                    )
                    is(4)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(2).asBits
                    )
                    is(8)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(3).asBits
                    )
                    is(16)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(4).asBits
                    )
                    is(32)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(5).asBits
                    )
                    is(64)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(6).asBits
                    )
                    is(128)(
                        pe_core_update_reg(i)(j)(k) := pecore_array(i).io_update.wr_data(7).asBits
                    )
                    is(255)(
                        pe_core_update_reg(i)(j)(k) := 0
                    )
                    default(pe_core_update_reg(i)(j)(k) := pe_core_update_reg(i)(j)(k))
                }
            }
        }
    }
    gather_pe_core.io_state.switch_done := swap_done
    for (i <- 0 until config.thread_num) {
        vertex_reg_group_A(i).io.in_stream.valid    := Mux(swap, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid,  False) , False)
        vertex_reg_group_A(i).io.in_stream.payload  := Mux(swap, Mux(vertex_reg_en(i)=== True, io.vertex_stream_top.payload, B(0)), B(0))
        vertex_reg_group_B(i).io.in_stream.valid    := Mux(!swap, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.valid, False), False)
        vertex_reg_group_B(i).io.in_stream.payload  := Mux(!swap, Mux(vertex_reg_en(i) === True, io.vertex_stream_top.payload, B(0)), B(0))
        vertex_reg_group_A(i).io.srst               := Mux(!swap, swap_done, False)
        vertex_reg_group_B(i).io.srst               := Mux(swap, swap_done, False)
        vertex_reg_group_A(i).io.rd_addr            := gather_pe_core.io_vertex.rd_addr(0).asUInt
        vertex_reg_group_B(i).io.rd_addr            := gather_pe_core.io_vertex.rd_addr(0).asUInt

        when (!swap && gather_pe_core.io_vertex.rd_addr(3 downto 1) === i ) {
            gather_pe_core.io_vertex.rd_data := vertex_reg_group_A(i).io.rd_data
        } elsewhen(swap && gather_pe_core.io_vertex.rd_addr(3 downto 1) === i ) {
            gather_pe_core.io_vertex.rd_data := vertex_reg_group_B(i).io.rd_data
        } otherwise {
            gather_pe_core.io_vertex.rd_data := 0
        }
    }

    gather_pe_busy := !gather_pe_core.io_state.gather_pe_done

    val rdy_list_B = Bits(config.thread_num bits)
    val rdy_list_A = Bits(config.thread_num bits)

    for (i <- 0 until config.thread_num) {
        rdy_list_A(i) := vertex_reg_en(i) & vertex_reg_group_A(i).io.in_stream.ready
        rdy_list_B(i) := vertex_reg_en(i) & vertex_reg_group_B(i).io.in_stream.ready
    }

    val vertex_reg_sel        = Reg(UInt(3 bits)) init 0
    io.vertex_stream_top.ready := Mux(swap, rdy_list_A(vertex_reg_sel),  rdy_list_B(vertex_reg_sel))

    //-----------------------------------------------------
    // Other Logic
    //-----------------------------------------------------

//    TODO this only support size 64 * 32 = 2048
    val vertex_reg_upperbound = Reg(UInt(6 bits)) init 7
    val vertex_reg_lowerbound = Reg(UInt(6 bits)) init 0

    when (swap_done) {
        vertex_reg_upperbound := vertex_reg_upperbound + 8
        vertex_reg_lowerbound := vertex_reg_lowerbound + 8
    } elsewhen (io.srst) {
        vertex_reg_upperbound := 7
        vertex_reg_lowerbound := 0
    }

    when(io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0) {
        vertex_reg_cnt  := vertex_reg_cnt + 1
    } elsewhen (swap_done || io.srst) {
        vertex_reg_cnt  := 0
    }

    when (vertex_reg_cnt === vertex_reg_lowerbound) {
        vertex_reg_en   := 0x00000001
        vertex_reg_sel  := 0
    } elsewhen (io.vertex_stream_top.valid && io.vertex_stream_top.payload === 0 && vertex_reg_en =/= 0) {
        vertex_reg_en   := vertex_reg_en |<< 1
        vertex_reg_sel  := vertex_reg_sel + 1
    }

    val write_ptr = Reg(UInt(6 bits)) init 0
    val write_valid = Reg(Bool()) init False

    for (i <- 0 until config.thread_num) {
        for (j <- 0 until 4) {
            pe_bundle_wire(i)(j) := pe_core_update_reg(j)(i)(write_ptr).asSInt
        }
        pe_update_value(i) := pe_bundle_wire(i).reduceBalancedTree(_ + _)
    }
    for (i <- 0 until config.thread_num) {
        update_mem(i).write(
            enable = write_valid,
            address = write_ptr,
            data = pe_update_value(i).asBits
        )
    }
    val read_vec = Bool()
    val update_mem_read_vec = Vec(Vec(Bits(31 bits),32), 8)
    read_vec := gather_pe_core.io_update.rd_addr(0)
    for (i <- 0 until 8) {
        for (k <- 0 until 32) {
            update_mem_read_vec(i)(k) := update_mem(i).readAsync( (k+read_vec.asUInt*32).resize(6))
        }
    }
    for (k <- 0 until 32) {
        gather_pe_core.io_update.rd_data(k) := update_mem_read_vec(gather_pe_core.io_update.rd_addr(3 downto 1))(k)
    }


    io.writeback_payload    := gather_pe_core.io_state.writeback_payload
    io.writeback_valid      := gather_pe_core.io_state.writeback_valid

    io.bundle_busy_table <> bundle_busy_table
    bundle_busy := bundle_busy_table.orR

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
//    TODO here logic seems
        OPERATE
          .whenIsActive {
              when (!last_update) {
                  last_update := io.last_update(0)
              }
              when(last_update & !bundle_busy & !gather_pe_busy) {
                  need_update := True
                  write_ptr   := 0
                  write_valid := True
                  goto(UPDATE_SUM_AND_SWITCH)
              }
          }
        UPDATE_SUM_AND_SWITCH
          .whenIsActive {
              when(last_update) {
                  last_update := False
              }
              when(need_update && write_ptr =/= 63) {
                  write_ptr     := write_ptr + 1
              } elsewhen (need_update && write_ptr === 63) {
                  for (i <- 0 until config.core_num) {
                      update_reg_srst(i) := True
                  }
                  write_valid := False
                  need_update := False
                  swap := !swap
                  swap_done := True
              } otherwise {
                  for (i <- 0 until config.core_num) {
                      update_reg_srst(i) := False
                  }
                  swap_done := False
                  when (bundle_busy) {
                      goto(OPERATE)
                  } otherwise {
                      goto(IDLE)
                  }
              }
          }
    }
}

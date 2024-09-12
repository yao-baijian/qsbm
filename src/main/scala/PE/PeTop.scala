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

package PE

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class PeTop(config:PeConfig) extends Component {

    val io = new Bundle {
        val last_update         = in Vec(Bool(), config.core_num) //big line ending
        val edge_stream         = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val vertex_stream_pe    = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val vertex_stream_ge    = slave Stream (Bits(config.axi_extend_width bits))
        val pe_rdy_table        = out Vec(Bool(), config.core_num)
        val bundle_busy_table   = out Vec(Bool(), config.core_num)
        val writeback_valid     = out Bool()
        val writeback_payload   = out Bits(config.axi_extend_width bits)
        val srst                = in Bool()
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val swap_done               = Reg(Bool())                       init False
    val swap                    = Reg(Bool())                       init True
    val bundle_busy             = Bool()
    val bundle_busy_table       = Vec(Bool(), config.core_num)
    val last_update             = Reg(Bool())                       init False
    val need_update             = Reg(Bool())                       init False
    val gather_pe_busy          = Bool()
    val update_reg_srst         = Vec(Reg(Bool()),config.core_num)
    val pe_rdy_table_all        = Vec(Vec(Bool(), config.thread_num),config.core_num)
    val all_zero                = Vec(Bool(), config.core_num)
    val Config                  = PeConfig()

    //-----------------------------------------------------
    // Module Declaration & Instantiation
    //-----------------------------------------------------
    val high_to_low_converter       = HighToLowConvert(Config)
    val gather_pe_core              = GatherPeCore(Config)
    val pecore_array                = new Array[PeCore](config.core_num)
    val vertex_reg_A                = DualModeReg(Config)
    val vertex_reg_B                = DualModeReg(Config)
    val update_mem                  = Mem(Bits(config.spmm_prec*32 bits), wordCount = 16)
    val pe_update_reg               = new Array[PeUpdateReg](config.core_num)

    for (i <- 0 until config.core_num) {
        pecore_array(i) = PeCore(Config)
        pecore_array(i).setName("pe_bundle_" + i.toString)
        pe_update_reg(i) = PeUpdateReg(Config)
        pe_update_reg(i).setName("pe_update_reg_" + i.toString)
    }

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        high_to_low_converter.io.in_edge_stream(i)  <> io.edge_stream(i)
        bundle_busy_table(i)                        <> pecore_array(i).io_state.pe_busy
        pecore_array(i).io_global_reg.vertex_stream <> io.vertex_stream_pe(i)
        pecore_array(i).io_state.last_update        <> io.last_update(i)
        pecore_array(i).io_state.all_zero           <> all_zero(i)
        pecore_array(i).io_state.swap_done          <> swap_done
        io.pe_rdy_table(i)                          := pe_rdy_table_all(i).orR
        all_zero(i)                                 := high_to_low_converter.io.all_zero(i)

        for (j <- 0 until config.thread_num) {
            pe_rdy_table_all(i)(j)              <> pecore_array(i).io_fifo.pe_fifo(j).ready
            pecore_array(i).io_fifo.pe_fifo(j)  <> high_to_low_converter.io.out_edge_stream(i)(j)
            pe_update_reg(i).io.wr_addr(j)      <> pecore_array(i).io_update.wr_addr(j)
            pe_update_reg(i).io.wr_valid(j)     <> pecore_array(i).io_update.wr_valid(j)
            pe_update_reg(i).io.wr_data(j)      <> pecore_array(i).io_update.wr_data(j)
            pe_update_reg(i).io.rd_addr(j)      <> pecore_array(i).io_update.rd_addr(j)
            pe_update_reg(i).io.rd_data(j)      <> pecore_array(i).io_update.rd_data(j)
        }
        pe_update_reg(i).io.wr_tag   <> pecore_array(i).io_update.wr_tag
        pe_update_reg(i).io.rd_tag   <> pecore_array(i).io_update.rd_tag
        pe_update_reg(i).io.update_reg_srst <> update_reg_srst(0)
    }
    gather_pe_core.io.swap_done         <> swap_done
    vertex_reg_A.io.rd_addr             <> gather_pe_core.io.rd_addr
    vertex_reg_B.io.rd_addr             <> gather_pe_core.io.rd_addr
    vertex_reg_A.io.in_stream.valid     := Mux(swap, io.vertex_stream_ge.valid , False)
    vertex_reg_A.io.in_stream.payload   := Mux(swap, io.vertex_stream_ge.payload,  B(0))
    vertex_reg_B.io.in_stream.valid     := Mux(!swap, io.vertex_stream_ge.valid, False)
    vertex_reg_B.io.in_stream.payload   := Mux(!swap, io.vertex_stream_ge.payload, B(0))
    gather_pe_core.io.vertex_rd_data    := Mux(!swap, vertex_reg_A.io.rd_data, vertex_reg_B.io.rd_data)
    io.vertex_stream_ge.ready           := Mux(swap, vertex_reg_A.io.in_stream.ready,  vertex_reg_B.io.in_stream.ready)
    gather_pe_busy                      := !gather_pe_core.io.gather_pe_done

    //-----------------------------------------------------
    // Other Logic
    //-----------------------------------------------------

    val write_ptr       = Reg(UInt(4 bits)) init 0
    val write_valid     = Reg(Bool()) init False
    val pe_update_value = Vec(Bits(config.spmm_prec bits), 32)
    val pe_bundle_wire  = Vec(Vec(SInt(config.spmm_prec bits), config.core_num), 32)

    for (i <- 0 until 4) {
        pe_update_reg(i).io.write_ptr := write_ptr
    }

    for (i <- 0 until 32) {
        for (j <- 0 until 4){
            pe_bundle_wire(i)(j) := pe_update_reg(j).io.pe_bundle_wire(j)
        }
        pe_update_value(i) := pe_bundle_wire(i).reduceBalancedTree(_ + _).asBits
    }

    update_mem.write(
        enable  = write_valid,
        address = write_ptr,
        data    = pe_update_value.reduce(_ ## _)
    )

    io.writeback_payload            <> gather_pe_core.io.writeback_payload
    io.writeback_valid              <> gather_pe_core.io.writeback_valid
    io.bundle_busy_table            <> bundle_busy_table
    gather_pe_core.io.spmm_rd_data  := update_mem.readAsync(gather_pe_core.io.rd_addr)
    bundle_busy                     := bundle_busy_table.orR

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
              when(need_update && write_ptr =/= 15) {
                  write_ptr     := write_ptr + 1
              } elsewhen (need_update && write_ptr === 15) {
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

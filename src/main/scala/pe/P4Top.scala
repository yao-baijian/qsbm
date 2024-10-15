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

package pe

import spinal.core.sim._
import spinal.core.{Reg, _}
import spinal.lib._
import cfg._
import spinal.lib.fsm._

import scala.language.postfixOps

case class P4Top() extends Component {

    val config = Config

    val io = new Bundle {
        val last_cb             = in Bool()
        val edge_stream         = Vec(slave Stream (Bits(config.axi_width bits)), config.core_num)
        val vertex_stream_pe    = Vec(slave Stream (Bits(config.axi_width bits)), config.core_num)
        val vertex_stream_ge    = slave Stream (Bits(config.axi_width bits))
        val pe_rdy_table        = out Vec(Bool(), config.core_num)
        val pe_busy             = out Vec(Bool(), config.core_num) simPublic()
        val ge_busy             = out Bool()
        val update_busy         = out Bool() setAsReg() init False simPublic()
        val writeback_valid     = out Bool()
        val writeback_payload   = out Bits(config.axi_width bits)
        val itr_cnt             = in  UInt (16 bits)
        val srst                = in Bool()
        val qsb_cfg 		        = slave(qsbConfig())
    }

    //-----------------------------------------------------
    // Val declaration
    //-----------------------------------------------------

    val swap_done        = Reg(Bool())  init False
    val swap             = Reg(Bool())  init True
    val pe_busy          = Vec(Bool(), config.core_num)
    val last_cb          = Reg(Bool())  init False
    val pe_rdy_table_all = Vec(Vec(Bool(), config.thread_num),config.core_num)
    val all_zero         = Vec(Bool(), config.core_num)

    val high2low_cvt     = Adapter()
    val gather_pe_core   = Ge()
    val pecore_array     = new Array[Pe](config.core_num)
    val pe_update_reg    = new Array[PeUpdateReg](config.core_num)
    val vertex_reg_A     = DualModeReg()
    val vertex_reg_B     = DualModeReg()

    val update_mem       = Mem(Bits(config.spmv_w * 32 bits), wordCount = 16) simPublic()
    val update_ptr       = Reg(UInt(4 bits)) init 0
    val pe_update_value  = Vec(SInt(config.spmv_w bits), 32)
    val pe_bundle_wire   = Vec(Vec(SInt(config.spmv_w bits), config.core_num), 32)
    val pe_update_bits   = Vec(pe_update_value.map(_.asBits))

    for (i <- 0 until config.core_num) {
        pecore_array(i) = Pe().setName("pe_" + i.toString)
        pe_update_reg(i) = PeUpdateReg().setName("pe_update_reg_" + i.toString)
    }

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        high2low_cvt.io.in_edge_stream(i)           <> io.edge_stream(i)
        pe_busy(i)                                  <> pecore_array(i).io_state.pe_busy
        pecore_array(i).io_global_reg.vertex_stream <> io.vertex_stream_pe(i)
        pecore_array(i).io_state.last_update        <> io.last_cb
        pecore_array(i).io_state.all_zero           <> all_zero(i)
        pecore_array(i).io_state.swap_done          <> swap_done
        io.pe_rdy_table(i)                          := pe_rdy_table_all(i).orR
        all_zero(i)                                 := high2low_cvt.io.all_zero(i)

        for (j <- 0 until config.thread_num) {
            pe_rdy_table_all(i)(j)              <> pecore_array(i).io_fifo.pe_fifo(j).ready
            pecore_array(i).io_fifo.pe_fifo(j)  <> high2low_cvt.io.out_edge_stream(i)(j)
            pe_update_reg(i).io.wr_addr(j)      <> pecore_array(i).io_update.wr_addr(j)
            pe_update_reg(i).io.wr_valid(j)     <> pecore_array(i).io_update.wr_valid(j)
            pe_update_reg(i).io.wr_data(j)      <> pecore_array(i).io_update.wr_data(j)
            pe_update_reg(i).io.rd_addr(j)      <> pecore_array(i).io_update.rd_addr(j)
            pe_update_reg(i).io.rd_data(j)      <> pecore_array(i).io_update.rd_data(j)
        }
        pe_update_reg(i).io.wr_tag      <> pecore_array(i).io_update.wr_tag
        pe_update_reg(i).io.rd_tag      <> pecore_array(i).io_update.rd_tag
        pe_update_reg(i).io.srst        <> swap_done
        pe_update_reg(i).io.update_valid := io.update_busy
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
    io.ge_busy                          := !gather_pe_core.io.gather_pe_done

    gather_pe_core.io.qsb_cfg <> io.qsb_cfg
    gather_pe_core.io.itr_cnt <> io.itr_cnt
    //-----------------------------------------------------
    // Other Logic
    //-----------------------------------------------------

    for (i <- 0 until 4) {
        pe_update_reg(i).io.update_ptr := update_ptr
    }

    for (i <- 0 until 32) {
        for (j <- 0 until 4){
            pe_bundle_wire(i)(j) := pe_update_reg(j).io.mem_wire(i)
        }
        pe_update_value(i) := pe_bundle_wire(i).reduceBalancedTree(_ + _)
    }

    update_mem.write(
        enable  = io.update_busy,
        address = update_ptr,
        data    = pe_update_bits.reverse.reduce(_ ## _)
    )

    io.writeback_payload            <> gather_pe_core.io.writeback_payload
    io.writeback_valid              <> gather_pe_core.io.writeback_valid
    io.pe_busy                      <> pe_busy
    gather_pe_core.io.spmv_result   := update_mem.readAsync(gather_pe_core.io.rd_addr)

    //-----------------------------------------------------
    // State Machine
    //-----------------------------------------------------

    val pe_fsm_top = new StateMachine {

        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val UPDATE_SUM_AND_SWITCH = new State

        IDLE
          .whenIsActive(
              when(pe_busy.orR) {
                  goto(OPERATE)
              }
          )
        OPERATE
          .whenIsActive {
              when (!last_cb) {
                  last_cb := io.last_cb
              }
              when(last_cb & !pe_busy.orR & !io.ge_busy) {
                  io.update_busy    := True
                  update_ptr        := 0
                  goto(UPDATE_SUM_AND_SWITCH)
              }
          }
        UPDATE_SUM_AND_SWITCH
          .onEntry {
              last_cb    := False
          }
          .whenIsActive {
              when(io.update_busy && update_ptr =/= 15) {
                  update_ptr      := update_ptr + 1
              } elsewhen (io.update_busy && update_ptr === 15) {
                  io.update_busy  := False
                  swap            := !swap
                  swap_done       := True
              } otherwise {
                  swap_done       := False
                  goto(IDLE)
              }
          }
    }
}

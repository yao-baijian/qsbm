/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
<<<<<<< HEAD
 * @LastEditTime: 2023-12-09 13:29:17
 * @LastEditTime: 2023-11-29 10:54:07
>>>>>>> parent of d5da2ed (fifo fix)
 * @FilePath: \sboom\src\main\scala\PE\GlobalReg.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */
package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps
   
case class GlobalReg(config: PeConfig) extends Component{

    val io = new Bundle{
        val in_stream       = slave Stream(Bits(config.axi_extend_width bits))
        val rd_addr         = Vec(in UInt(config.addr_width bits), config.thread_num)
        val rd_data         = Vec(out Bits(config.data_width bits), config.thread_num)
        val srst            = in Bool()
        val reg_full        = out Bool()
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------
    val vertex_mem  = Mem(Bits(config.axi_extend_width bits), wordCount = 2)
    val wr_pointer  = Reg(UInt(1 bits)) init 0
    val ready       = Reg(Bool()) init True
    val reg_full    = Reg(Bool()) init False

    //-----------------------------------------------------
    // Module Wiring
    //-----------------------------------------------------

    vertex_mem.write(
        enable = io.in_stream.valid && io.in_stream.ready,
        address = wr_pointer,
        data = io.in_stream.payload
    )

    for (i <- 0 until config.thread_num) {
        io.rd_data(i) := vertex_mem.readAsync(io.rd_addr(i)(5).asUInt).subdivideIn(config.data_width bits)(io.rd_addr(i)(4 downto 0))
    }


    io.in_stream.ready  := ready
    io.reg_full         := reg_full

    when (wr_pointer === 1) {
        ready := False
    } elsewhen (io.srst) {
        ready := True
    }

    when(wr_pointer === 1) {
        reg_full := True
    } otherwise {
        reg_full := False
    }

    when(io.in_stream.valid && io.in_stream.ready) {
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }


}
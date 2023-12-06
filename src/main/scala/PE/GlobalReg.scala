/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
 * @LastEditTime: 2023-11-29 10:54:07
 * @FilePath: \sboom\src\main\scala\PE\GlobalReg.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */



package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class GlobalReg(config: GlobalRegConfig) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(config.stream_width bits))

        val rd_addr   = Vec(in UInt(config.addr_width bits), 8)
        val rd_data   = Vec(out Bits(config.data_width bits), 8)

        val need_new_vertex   = in Bool()
        val reg_full  = out Bool()
    }

    val vertex_reg = Vec.fill(config.reg_depth)(Reg(Bits(config.data_width bits)))
    vertex_reg.foreach(_ init(0))
    val wr_pointer = Reg(UInt(3 bits)) init (0)

    val rdy = Reg(Bool()) init(True)

    when (wr_pointer === 7) {
        rdy := False
    } elsewhen (io.need_new_vertex) {
        rdy := True
    }

    io.in_stream.ready := rdy
    io.reg_full := !io.in_stream.ready

    when(io.in_stream.valid && io.in_stream.ready) {
        for (i <- 0 until 8) {
            vertex_reg((wr_pointer*8)(5 downto 0) + i) := io.in_stream.payload(16*(i + 1) - 1 downto 16*i )
        }
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }

    for (i <- 0 until 8) {
        io.rd_data (i) := vertex_reg(io.rd_addr(i))
    }


}
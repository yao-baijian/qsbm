package PE

import spinal.core._
import spinal.lib._
/*
 * @Author: byao
 * @Date: 2023-11-09 11:22:37
 * @LastEditors:  
 * @LastEditTime: 2023-11-17 15:08:41
 * @FilePath: \sbm_sparse_gitee\src\main\scala\PE\fifo.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */

case class Fifo(
    
    reg_depth: Int      = 256,
    reg_width: Int      = 16,
    stream_width: Int   = 128

) extends Component{
    
    val io = new Bundle{
        val in_stream = slave Stream(Bits(reg_width bits))
        val out_stream = master Stream(Bits(reg_width bits))
        val fifo_done = out Bool()
        val rst = in Bool()
    }

    val edge_fifo = StreamFifo(Bits(reg_width bits), reg_depth)
    val pay_load_buf =  Reg(Bits(reg_width bits))

    when (io.in_stream.payload === 0x0000000000000000) {
        io.fifo_done := True
    }.elsewhen (io.rst) {
        io.fifo_done := False
    }

    when (io.fifo_done) {
        pay_load_buf := io.in_stream.payload
    }

    edge_fifo.io.push.valid := io.in_stream.valid && !io.fifo_done
    edge_fifo.io.push.payload := pay_load_buf

    io.in_stream.ready := edge_fifo.io.push.ready
    edge_fifo.io.pop << io.out_stream

}
/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
 * @LastEditTime: 2023-11-29 10:54:32
 * @FilePath: \sboom\src\main\scala\PE\Fifo.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */

package PE

import spinal.core._
import spinal.lib._

case class Fifo(

     fifo_depth: Int      = 256,
     data_width: Int      = 16,
     stream_width: Int   = 128

) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(data_width bits))
        val out_stream = master Stream(Bits(data_width bits))
        val fifo_done = out Bool()
        val rst = in Bool()
    }

    val edge_fifo = StreamFifo(Bits(data_width bits), fifo_depth)
    val pay_load_buf =  Reg(Bits(data_width bits))

    //TODO: stream rdy is wrongly driven
    
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
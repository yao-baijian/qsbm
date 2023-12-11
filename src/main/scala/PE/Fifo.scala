/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
 * @LastEditTime: 2023-12-09 13:14:12
 * @FilePath: \sboom\src\main\scala\PE\Fifo.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */

package PE

import spinal.core._
import spinal.lib._
import scala.language.postfixOps

case class Fifo(config:FifoConfig) extends Component{
  
    val io = new Bundle{
        val new_edge = in(Bool())
        val in_stream = slave Stream(Bits(config.data_width bits))
        val out_stream = master Stream(Bits(config.data_width bits))
    }

    val edge_fifo = StreamFifo(Bits(config.data_width bits), config.fifo_depth)
    val in_stream_valid = Reg(Bool()) init True
    val in_stream_ready = Reg(Bool()) init True

    when(io.in_stream.payload === 0 & io.in_stream.valid) {
        in_stream_valid := False
        in_stream_ready := False
    } elsewhen (io.new_edge === True) {
        in_stream_valid := True
        in_stream_ready := True
    }

    edge_fifo.io.push.valid := in_stream_valid & io.in_stream.valid
    io.in_stream.ready      := edge_fifo.io.push.ready &  in_stream_ready
    edge_fifo.io.push.payload := io.in_stream.payload

    edge_fifo.io.pop >> io.out_stream
}
/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
 * @LastEditTime: 2023-11-30 10:12:30
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
        val in_stream = slave Stream(Bits(config.data_width bits))
        val out_stream = master Stream(Bits(config.data_width bits))
    }

    val edge_fifo = StreamFifo(Bits(config.data_width bits), config.fifo_depth)
    edge_fifo.io.pop >> io.out_stream
    edge_fifo.io.push << io.in_stream
}
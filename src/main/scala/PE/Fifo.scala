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
    
    reg_depth: Int = 64,
    reg_width: Int = 128

) extends Component{
    
    val io = new Bundle{
        val in_stream = slave Stream(Bits(reg_width))
        val out_stream = master Stream(Bits(reg_width))
    }

    val edge_Fifo = StreamFifo(Bits(reg_width), reg_depth)
    val input_edge_valid = Reg(Bool) init True

    when (slave.payload == 0x0000000000000000) {
        input_edge_valid := False
    }.elsewhen (!in_stream.valid) {
        input_edge_valid := True
    } otherwise {
        input_edge_valid := input_edge_valid
    }

    edge_Fifo.io.push.valid := in_stream.valid && input_edge_valid
    edge_Fifo.io.push.payload := in_stream.payload
    in_stream.ready := edge_Fifo.io.push.ready
    edge_Fifo.io.pop << io.out_stream

}
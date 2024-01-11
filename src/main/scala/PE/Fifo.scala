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
import spinal.lib.fsm._

import scala.language.postfixOps

case class Fifo(config:PeConfig) extends Component{

    val io = new Bundle{
        val globalreg_done  = in(Bool())
        val in_stream       = slave Stream(Bits(config.data_width bits))
        val out_stream      = master Stream(Bits(config.data_width bits))
        val all_zero        = in(Bool())
    }

    val edge_fifo       = StreamFifo(Bits(config.data_width bits), config.fifo_depth_128)
    val in_stream_valid = Reg(Bool()) init True
    val in_stream_ready = Reg(Bool()) init True

    val pe_fsm = new StateMachine {

        val IDLE = new State with EntryPoint
        val READ = new State
        val WAIT = new State

        IDLE
          .whenIsActive {
              when(io.in_stream.valid === True) {
                  goto(READ)
              }
          }

        READ
          .whenIsActive {
              when(io.all_zero) {
                  in_stream_valid := False
                  in_stream_ready := False
                  goto(WAIT)
              }
          }
        WAIT
          .whenIsActive {
              when(io.globalreg_done === True) {
                  in_stream_valid := True
                  in_stream_ready := True
                  goto(IDLE)
              }
          }
        }

    edge_fifo.io.push.valid     := in_stream_valid & io.in_stream.valid
    edge_fifo.io.push.payload   := io.in_stream.payload
    io.in_stream.ready          := edge_fifo.io.push.ready & in_stream_ready

    edge_fifo.io.pop >> io.out_stream
}
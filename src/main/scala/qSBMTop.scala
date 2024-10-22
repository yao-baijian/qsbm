package test

import disp._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.{master, slave}
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4Config}
import cfg._

case class qSBMTop() extends Component{

    val config =Config

    val io = new Bundle {
        val topAxiMemControlPort = master(Axi4(Axi4Config(addressWidth = 32, dataWidth = config.axi_width, idWidth = 4)))
        val topAxiLiteSlave = slave(AxiLite4(AxiLite4Config(addressWidth = 32, dataWidth = 32)))
        val done = out Bool()
        val update_busy = out Bool() simPublic()
        val ge_busy = out Bool() simPublic()
    }

    noIoPrefix()

    axiRename(io.topAxiMemControlPort, "M00_AXI_")
    axiLiteRename(io.topAxiLiteSlave,  "S00_AXI_")

    val ctrl_reg    = AxiLiteReg()
    val dispatcher  = Dispatcher()
    val pe_top      = pe.P4Top()

    // top axi
    io.topAxiMemControlPort << dispatcher.io.axiMemControlPort
    io.done := dispatcher.io.done
    // ctrl reg - dispatcher
    ctrl_reg.io.axi_lite  << io.topAxiLiteSlave
    dispatcher.io.start 	:= ctrl_reg.io.start(0)
    dispatcher.io.srst   	:= ctrl_reg.io.srst(0)
    ctrl_reg.io.done      := dispatcher.io.done
    dispatcher.io.qsb_cfg <> ctrl_reg.io.qsb_cfg
    dispatcher.io.vex_a_base:= ctrl_reg.io.vex_a_base
    dispatcher.io.vex_b_base:= ctrl_reg.io.vex_b_base
    dispatcher.io.edge_base := ctrl_reg.io.edge_base

    pe_top.io.qsb_cfg   <> ctrl_reg.io.qsb_cfg
    pe_top.io.itr_cnt   := dispatcher.io.itr_cnt

    // pe - dispatcher
    pe_top.io.vertex_stream_ge.payload  := dispatcher.io.vex2ge.payload.data
    pe_top.io.vertex_stream_ge.valid    := dispatcher.io.vex2ge.valid
    pe_top.io.last_cb                   := dispatcher.io.RB_switch

    for(i<-0 until config.core_num){
        dispatcher.io.pe_busy(i)      := pe_top.io.pe_busy(i)
        pe_top.io.vertex_stream_pe(i).payload   := dispatcher.io.vex2pe(i).payload.data
        pe_top.io.vertex_stream_pe(i).valid     := dispatcher.io.vex2pe(i).valid

        pe_top.io.edge_stream(i).payload        := dispatcher.io.edge2pe(i).payload.data
        pe_top.io.edge_stream(i).valid          := dispatcher.io.edge2pe(i).valid
        dispatcher.io.edge2pe(i).ready          := pe_top.io.edge_stream(i).ready
    }

    dispatcher.io.update_busy := pe_top.io.update_busy

    io.update_busy := pe_top.io.update_busy
    io.ge_busy     := pe_top.io.ge_busy

    // dispatcher - ddr
    dispatcher.io.wb_valid :=  pe_top.io.writeback_valid
    dispatcher.io.wb_payload := pe_top.io.writeback_payload

    // misc
    def axiRename(axi: Axi4, prefix: String): Unit = {
        axi.flattenForeach { bt =>
        val names = bt.getName().split("_")
        val channelName = names(1)
        val signalName = names.last
        val newName = (channelName ++ signalName).toUpperCase
        bt.setName(prefix ++ newName)
        }
    }

    def axiLiteRename(axi: AxiLite4, prefix: String): Unit = {
        axi.flattenForeach { bt =>
        val names = bt.getName().split("_")
        val channelName = names(1)
        val signalName = names.last
        val newName = (channelName ++ signalName).toUpperCase
        bt.setName(prefix ++ newName)
        }
    }
}

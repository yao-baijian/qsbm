package dispatcher

import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import PE.qsbConfig

case class AxiLiteReg() extends Component {

	val io = new Bundle{

		val axi_lite		= slave(AxiLite4(32, 32))
		val start				= out UInt(32 bits) setAsReg() init 0
		val srst   			= out UInt(32 bits) setAsReg() init 0
		val done 				= in Bool()
		val qsb_cfg 		= master(qsbConfig())
		val vex_a_base 	= out UInt(32 bits) setAsReg() init 0
		val vex_b_base 	= out UInt(32 bits) setAsReg() init 0
		val edge_base 	= out UInt(32 bits) setAsReg() init 0
	}

	val axiLiteCtrl = AxiLite4SlaveFactory(io.axi_lite)
  axiLiteCtrl.readAndWrite(io.start, address = 0x00)
  axiLiteCtrl.readAndWrite(io.srst, address = 0x04)
  axiLiteCtrl.read(io.done, address = 0x08)
	axiLiteCtrl.readAndWrite(io.qsb_cfg.iteration, address = 0x0C)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.matrix_size, address = 0x10)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.tile_xy, address = 0x14)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.CB_max, address = 0x18)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.CB_init, address = 0x1C)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.RB_init, address = 0x20)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.ai_init, address = 0x24)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.ai_incr, address = 0x28)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.xi, address = 0x2C)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.dt, address = 0x30)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.RB_max, address = 0x40)
  axiLiteCtrl.readAndWrite(io.qsb_cfg.CB_length, address = 0x44)

  axiLiteCtrl.readAndWrite(io.vex_a_base, address = 0x34)
  axiLiteCtrl.readAndWrite(io.vex_b_base, address = 0x38)
  axiLiteCtrl.readAndWrite(io.edge_base, address = 0x3C)
		
  noIoPrefix()
}

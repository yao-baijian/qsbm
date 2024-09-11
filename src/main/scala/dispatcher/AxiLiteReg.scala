package dispatcher

import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import spinal.lib.tools.DataAnalyzer
import PE.qsbConfig

case class AxiLiteReg() extends Component {

	val io = new Bundle{

		val axi_lite	= slave(AxiLite4(32, 32))
		val disp    	= in Bits(4 bits)
		val mem_ctrl	= in Bits(4 bits)
		val start		= out UInt(32 bits)
		val srst   		= out UInt(32 bits)
		val done 		= in Bits(4 bits)
		val qsb_cfg 	= master(qsbConfig())
		val vex_a_base 	= out UInt(32 bits)
  		val vex_b_base 	= out UInt(32 bits)
  		val edge_base 	= out UInt(32 bits)
	}

	val axiLiteCtrl = AxiLite4SlaveFactory(io.axi_lite)
	val start  		= axiLiteCtrl.driveAndRead(io.start, address = 0x00) init 0
	val srst        = axiLiteCtrl.createReadAndWrite(io.srst, address = 0x04) init 0
	val done 		= axiLiteCtrl.createReadOnly(io.done, address = 0x08) init 0
	val iteration 	= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.iteration, address = 0x0C) init 0
	val matrix_size = axiLiteCtrl.createReadAndWrite(io.qsb_cfg.matrix_size, address = 0x10) init 0

	val tile_xy 	= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.tile_xy, address = 0x14) init 0
	val CB_max 		= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.CB_max, address = 0x18) init 0

	val CB_init     = axiLiteCtrl.createReadAndWrite(io.qsb_cfg.CB_init, address = 0x1C) init 0
	val RB_init     = axiLiteCtrl.createReadAndWrite(io.qsb_cfg.RB_init, address = 0x20) init 0
	val ai_init 	= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.ai_init, address = 0x24) init 0
    val ai_incr 	= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.ai_incr, address = 0x28) init 0
	val xi 			= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.xi, address = 0x2C) init 0
	val dt 			= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.dt, address = 0x30) init 0

	val vex_a_base 	= axiLiteCtrl.createReadAndWrite(io.vex_a_base, address = 0x34) init 0
  	val vex_b_base 	= axiLiteCtrl.createReadAndWrite(io.vex_b_base, address = 0x38) init 0
  	val edge_base 	= axiLiteCtrl.createReadAndWrite(io.edge_base, address = 0x3C) init 0

	val RB_max 		= axiLiteCtrl.createReadAndWrite(io.qsb_cfg.RB_max, address = 0x40) init 0
		
  	noIoPrefix()
}

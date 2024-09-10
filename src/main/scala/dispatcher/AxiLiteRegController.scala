package dispatcher
import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import spinal.lib.tools.DataAnalyzer

case class AxiLiteRegController() extends Component {

	val io = new Bundle{

		val axi_lite= slave(AxiLite4(32, 32))
		val disp    = in Bits(4 bits)
		val mem_ctrl= in Bits(4 bits)
		val done 	= in Bits(4 bits)

		val start	= out UInt(32 bits)
		val srst   	= out UInt(32 bits)
		val CB_init = out UInt(32 bits)
		val RB_init = out UInt(32 bits)
		val ai_init = out UInt(32 bits)
		val ai_incr = out UInt(32 bits)
		val xi 		= out UInt(32 bits)
		val dt 		= out UInt(32 bits)
	}

	val axiLiteCtrl = AxiLite4SlaveFactory(io.axi_lite)

	var addr_base   = 2*1024*1024*1024

	val start_flag  = axiLiteCtrl.driveAndRead(io.start, address = 0x00) init 0
	val srst        = axiLiteCtrl.createReadAndWrite(io.srst, address = 0x04) init 0
	val CB_init     = axiLiteCtrl.createReadAndWrite(io.CB_init, address = 0x08) init 0
	val RB_init     = axiLiteCtrl.createReadAndWrite(io.RB_init, address = 0x12) init 0
	val ai_init 	= axiLiteCtrl.createReadAndWrite(io.ai_init, address = 0x16) init 0
    val ai_incr 	= axiLiteCtrl.createReadAndWrite(io.ai_incr, address = 0x20) init 0
	val xi 			= axiLiteCtrl.createReadAndWrite(io.xi, address = 0x24) init 0
	val dt 			= axiLiteCtrl.createReadAndWrite(io.dt, address = 0x28) init 0

	val done_1 		= axiLiteCtrl.createReadOnly(io.done, address = 0x32) init 0
  	noIoPrefix()
}

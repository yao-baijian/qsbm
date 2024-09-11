package PE

import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._

case class qsbConfig() extends Bundle with IMasterSlave{

    val iteration 	= UInt(32 bits)
    val matrix_size = UInt(32 bits)
    val tile_xy 	= UInt(32 bits)
    val CB_max 		= UInt(32 bits)
    val RB_max 		= UInt(32 bits)
    val CB_init   = UInt(32 bits)
    val RB_init   = UInt(32 bits)
    val ai_init   = UInt(32 bits)
    val ai_incr   = UInt(32 bits)
    val xi 		    = UInt(32 bits)
    val dt 		    = UInt(32 bits)

    override def asMaster(): Unit = {
        out(iteration) setAsReg() init(0)
        out(matrix_size) setAsReg() init(0)
        out(tile_xy) setAsReg() init(0)
        out(CB_max) setAsReg() init(0)
        out(RB_max) setAsReg() init(0)
        out(CB_init) setAsReg() init(0)
        out(RB_init) setAsReg() init(0)
        out(ai_init) setAsReg() init(0)
        out(ai_incr) setAsReg() init(0)
        out(xi) setAsReg() init(0)
        out(dt) setAsReg() init(0)
    }

    override def asSlave(): Unit = {
        in(iteration)
        in(matrix_size)
        in(tile_xy)
        in(CB_max)
        in(RB_max)
        in(CB_init)
        in(RB_init)
        in(ai_init)
        in(ai_incr)
        in(xi)
        in(dt)
    }
}
package PE

import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._

case class qsbConfig() extends Bundle with IMasterSlave{

    val CB_init = UInt(32 bits)
    val RB_init = UInt(32 bits)
    val ai_init = UInt(32 bits)
    val ai_incr = UInt(32 bits)
    val xi 		= UInt(32 bits)
    val dt 		= UInt(32 bits)

    override def asMaster(): Unit = {
        out(CB_init)
        out(RB_init)
        out(ai_init)
        out(ai_incr)
        out(xi)
        out(dt)
    }

    override def asSlave(): Unit = {
        in(CB_init)
        in(RB_init)
        in(ai_init)
        in(ai_incr)
        in(xi)
        in(dt)
    }
}
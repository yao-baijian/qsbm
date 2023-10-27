package dispatcher
import spinal.core._
import spinal.lib._

import scala.language.postfixOps
//import spinal.core.{Bundle, Component}

case class AxiMemCtrl() extends Component{

  val io = new Bundle{

    val a = in UInt(8 bits)
    val b = in UInt(8 bits)
    val prod: UInt = out (Reg(UInt(16 bits)))

  }

  io.prod := io.a * io.b

}

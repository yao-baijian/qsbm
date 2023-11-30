import dispatcher.Dispatcher
import spinal.core._
import spinal.lib.bus.amba4.axi.Axi4

case class SboomTop() extends Component{

//  val axiConfig = Axi4Config(
//    addressWidth = 32,
//    dataWidth = 128,
//    idWidth = 4
//  )

  def axiRename(axi: Axi4, prefix: String): Unit = {
    axi.flattenForeach { bt =>
      val names = bt.getName().split("_")
      val channelName = names(1)
      val signalName = names.last
      val newName = (channelName ++ signalName).toUpperCase
      bt.setName(prefix ++ newName)
    }
  }

  val io = new Bundle{


  }

  noIoPrefix()

//  axiRename(io.topAxiMemPort, "M_AXI_")
  val dispatcher = Dispatcher()

}

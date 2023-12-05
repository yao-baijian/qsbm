import dispatcher.Dispatcher
import spinal.core._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.master
import dispatcher._

case class SboomTop() extends Component{

  val axiConfig = Axi4Config(addressWidth = 32, dataWidth = 128, idWidth = 4)

  val io = new Bundle {
    val topAxiMemControlPort = master(Axi4(axiConfig))
  }

  noIoPrefix()

  def axiRename(axi: Axi4, prefix: String): Unit = {
    axi.flattenForeach { bt =>
      val names = bt.getName().split("_")
      val channelName = names(1)
      val signalName = names.last
      val newName = (channelName ++ signalName).toUpperCase
      bt.setName(prefix ++ newName)
    }
  }

//  axiRename(io.topAxiMemPort, "M_AXI_")
  val dispatcher = Dispatcher()
  val pe_top_config = PE.PETopConfig()
  val peTop = PE.PeTop(pe_top_config)

  peTop.io.vertex_stream_top.payload := dispatcher.io.dispatchToVexRegFilePorts(0).payload.data
  io.topAxiMemControlPort << dispatcher.io.axiMemControlPort
  for(i <- 0 until PeConfig().peColumnNum){

    dispatcher.io.bigPeReadyFlagVec(i) := True

  }

}

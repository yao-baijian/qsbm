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

  io.topAxiMemControlPort << dispatcher.io.axiMemControlPort

  //******************************** control signals connection *********************//
  for(i<- 0 until PeConfig().peColumnNum){
    dispatcher.io.bigPeBusyFlagVec(i) := peTop.io.bundle_busy_table(i)
    peTop.io.last_update(i) := dispatcher.io.switchBigLineFlag
    dispatcher.io.edgeFifoReadyVec(i) := peTop.io.pe_rdy_table(i)
  }



  //********************************** vertex connection ***************************//
  //RegOut4Gather connections
  peTop.io.vertex_stream_top.payload := dispatcher.io.dispatchVexRegOut4Gather.payload.data
  peTop.io.vertex_stream_top.valid := dispatcher.io.dispatchVexRegOut4Gather.valid
//  dispatcher.io.dispatchVexRegOut4Gather.ready := peTop.io.vertex_stream_top.ready

  // dispatch vertex to big PEs
  for(i<-0 until PeConfig().peColumnNum){
    peTop.io.vertex_stream(i).payload := dispatcher.io.dispatchToVexRegFilePorts(i).payload.data
    peTop.io.vertex_stream(i).valid := dispatcher.io.dispatchToVexRegFilePorts(i).valid
  }

  //********************************** edge connection *****************************//
  for(i <- 0 until PeConfig().peColumnNum){

//    val pe0Ready = dispatcher.io.dispatchToEdgeFifoPorts(i).reduceLeft(_.ready && _.ready)
//    dispatcher.io.bigPeBusyFlagVec(i) := True
    for(j <- 0 until PeConfig().peNumEachColumn){
      peTop.io.edge_stream(i)(j).payload := dispatcher.io.dispatchToEdgeFifoPorts(i)(j).payload.data
      peTop.io.edge_stream(i)(j).valid := dispatcher.io.dispatchToEdgeFifoPorts(i)(j).valid
      dispatcher.io.dispatchToEdgeFifoPorts(i)(j).ready := peTop.io.edge_stream(i)(j).ready
    }
  }

//  val pe0Ready = dispatcher.io.dispatchToEdgeFifoPorts

}

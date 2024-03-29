import dispatcher.Dispatcher
import spinal.core._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.master
import dispatcher._

case class SboomTop() extends Component{

  val axiConfig = Axi4Config(addressWidth = 32, dataWidth = 512, idWidth = 4)
  val axiEdgeIndexPortConfig = Axi4Config(addressWidth = 32, dataWidth = 128, idWidth = 4)

  val io = new Bundle {
    val topAxiMemControlPort = master(Axi4(axiConfig))
    val topAxiEdgeIndexPort = master(Axi4(axiEdgeIndexPortConfig))
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
  val pe_top_config = PE.PeConfig()
  val peTop = PE.PeTop(pe_top_config)

  io.topAxiMemControlPort << dispatcher.io.axiMemControlPort
  io.topAxiEdgeIndexPort << dispatcher.io.axiEdgeIndexPort

  //******************************** control signals connection *********************//
  for(i<- 0 until PeConfig().peColumnNum){
    dispatcher.io.bigPeBusyFlagVec(i) := peTop.io.bundle_busy_table(i)
    peTop.io.last_update(i) := dispatcher.io.bigLineSwitchFlag
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

  //********************************** edge Index port connection from dispatcher to PE ***************************//
  for(i <- 0 until PeConfig().peColumnNum){

    peTop.io.tag_stream(i).payload := dispatcher.io.dispatchToedgeIndexPorts(i).payload.data
    peTop.io.tag_stream(i).valid := dispatcher.io.dispatchToedgeIndexPorts(i).valid
    dispatcher.io.dispatchToedgeIndexPorts(i).ready := peTop.io.tag_stream(i).ready

  }

  //********************************** edge port connection from dispatcher to PE *******************************//
  for(i <- 0 until PeConfig().peColumnNum){

    peTop.io.bundle_sel(i) := dispatcher.io.edgePeColumnSelectOH(i)
//    val pe0Ready = dispatcher.io.dispatchToEdgeFifoPorts(i).reduceLeft(_.ready && _.ready)
//    dispatcher.io.bigPeBusyFlagVec(i) := True
//    for(j <- 0 until PeConfig().peNumEachColumn){
      peTop.io.edge_stream(i).payload := dispatcher.io.dispatchToEdgePorts(i).payload.data
      peTop.io.edge_stream(i).valid := dispatcher.io.dispatchToEdgePorts(i).valid
      dispatcher.io.dispatchToEdgePorts(i).ready := peTop.io.edge_stream(i).ready
//    }
  }

//  val pe0Ready = dispatcher.io.dispatchToEdgeFifoPorts

}

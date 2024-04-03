import dispatcher._
import spinal.core._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.{master, slave}
import dispatcher._
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4Config}

case class SboomTop() extends Component{

  val axiConfig = Axi4Config(addressWidth = 32, dataWidth = 512, idWidth = 4)
  val axiEdgeIndexPortConfig = Axi4Config(addressWidth = 32, dataWidth = 128, idWidth = 4)
  val axiLiteConfig = AxiLite4Config(addressWidth = 32, dataWidth = 32)

  val io = new Bundle {
    val topAxiMemControlPort = master(Axi4(axiConfig))
    val topAxiEdgeIndexPort = master(Axi4(axiEdgeIndexPortConfig))
    val topAxiLiteSlave = slave(AxiLite4(axiLiteConfig))
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

  def axiLiteRename(axi: AxiLite4, prefix: String): Unit = {
    axi.flattenForeach { bt =>
      val names = bt.getName().split("_")
      val channelName = names(1)
      val signalName = names.last
      val newName = (channelName ++ signalName).toUpperCase
      bt.setName(prefix ++ newName)
    }
  }

  axiRename(io.topAxiMemControlPort, "M00_AXI_")
  axiRename(io.topAxiEdgeIndexPort,  "M01_AXI_")
  axiLiteRename(io.topAxiLiteSlave,  "S00_AXI_")

  val axiLiteRegCtrl = AxiLiteRegController()
  val dispatcher = Dispatcher()
  val pe_top_config = PE.PeConfig()
  val peTop = PE.PeTop(pe_top_config)

  io.topAxiMemControlPort << dispatcher.io.axiMemControlPort
  io.topAxiEdgeIndexPort << dispatcher.io.axiEdgeIndexPort

  //AxiLiteRegCtrl signals
  axiLiteRegCtrl.io.axiLiteSlave << io.topAxiLiteSlave
  dispatcher.io.read_flag := axiLiteRegCtrl.io.sbmConfigPort.startConfigRegOut(0)
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

  //write back connection from PE to Dispatcher
  dispatcher.io.writeback_valid :=  peTop.io.writeback_valid
  dispatcher.io.writeback_payload := peTop.io.writeback_payload

//  val pe0Ready = dispatcher.io.dispatchToEdgeFifoPorts

}

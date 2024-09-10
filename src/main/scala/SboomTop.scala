import dispatcher._
import spinal.core._
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.{master, slave}
import dispatcher._
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4Config}

case class SboomTop() extends Component{

  val axiConfig = Axi4Config(addressWidth = 32, dataWidth = 512, idWidth = 4)
  val axiLiteConfig = AxiLite4Config(addressWidth = 32, dataWidth = 32)

  val io = new Bundle {
    val topAxiMemControlPort = master(Axi4(axiConfig))
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
  axiLiteRename(io.topAxiLiteSlave,  "S00_AXI_")

  val axiLiteRegCtrl = AxiLiteRegController()
  val dispatcher = Dispatcher()
  val pe_top_config = PE.PeConfig()
  val peTop = PE.PeTop(pe_top_config)

  io.topAxiMemControlPort << dispatcher.io.axiMemControlPort

  //AxiLiteRegCtrl signals
  axiLiteRegCtrl.io.axi_lite << io.topAxiLiteSlave
  dispatcher.io.start := axiLiteRegCtrl.io.start(0)


  for(i<- 0 until PeConfig().peColumnNum){
    dispatcher.io.pe_busy(i) := peTop.io.bundle_busy_table(i)
    peTop.io.last_update(i) := dispatcher.io.RB_switch
    dispatcher.io.edgeFifoReadyVec(i) := peTop.io.pe_rdy_table(i)
  }
  peTop.io.vertex_stream_ge.payload := dispatcher.io.vex2ge.payload.data
  peTop.io.vertex_stream_ge.valid := dispatcher.io.vex2ge.valid
  for(i<-0 until PeConfig().peColumnNum){
    peTop.io.vertex_stream_pe(i).payload := dispatcher.io.vex2pe(i).payload.data
    peTop.io.vertex_stream_pe(i).valid := dispatcher.io.vex2pe(i).valid
  }
  for(i <- 0 until PeConfig().peColumnNum){
    peTop.io.edge_stream(i).payload := dispatcher.io.edge2pe(i).payload.data
    peTop.io.edge_stream(i).valid := dispatcher.io.edge2pe(i).valid
    dispatcher.io.edge2pe(i).ready := peTop.io.edge_stream(i).ready
  }

  dispatcher.io.writeback_valid :=  peTop.io.writeback_valid
  dispatcher.io.writeback_payload := peTop.io.writeback_payload

}

package dispatcher
import spinal.core.Component.push
import spinal.core._
import spinal.lib._
import spinal.lib.{Stream, _}
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.tools.DataAnalyzer
import spinal.lib.fsm._
//import scala.language.postfixOps
//import scala.sys.exit
//import spinal.core.{Bundle, Component}
case class AxiMemControllerPort(dataWidth:Int) extends Bundle {
  val data = Bits(DispatcherConfig().size bits)
  // def isBlack : Bool = red === 0 && green === 0 && blue === 0
}

case class AxiEdgeIndexPort(dataWidth:Int) extends Bundle {
  val data = Bits(DispatcherConfig().edgeIndexSize bits)
  // def isBlack : Bool = red === 0 && green === 0 && blue === 0
}

case class VexPeColumnNumFifoPort(dataWidth:Int) extends Bundle {
  val data = UInt(DispatcherConfig().vexPeColumnNumFifoWidth bits)
}

case class StreamFifoPort(dataWidth:Int) extends Bundle{
  val data = Bits(DispatcherConfig().fifoWidth bits)
}
case class Dispatcher() extends Component {

  val axiConfig = Axi4Config(addressWidth = AxiConfig().addrWid, dataWidth = AxiConfig().dataWid, idWidth = AxiConfig().idWid)
  val axiEdgeIndexPortConfig = Axi4Config(addressWidth = 32, dataWidth = 128, idWidth = 4)

  val io = new Bundle {

    val axiMemControlPort = master(Axi4(axiConfig))
    val axiEdgeIndexPort = master(Axi4(axiEdgeIndexPortConfig))

    val read_flag = in Bool()

    val result = out Bool()
    //    val test = in Bool()
    val switchBigLineFlag = out(Reg(Bool()) init False)
    val bigPeBusyFlagVec = in Vec(Bool(),PeConfig().peColumnNum)
    val edgeFifoReadyVec = in Vec(Bool(),PeConfig().peColumnNum)

    //4 column PEs
    val dispatchToVexRegFilePorts = Vec(master(Flow(AxiMemControllerPort(DispatcherConfig().size))), PeConfig().peColumnNum)

    //4 column PEs and in each column there are 4 master ports connected with 4 edge fifos
    //    val dispatchToEdgeFifoPorts = Vec(Vec(master(Stream(StreamFifoPort(DispatcherConfig().fifoWidth))), PeConfig().peNumEachColumn), PeConfig().peColumnNum)
    val edgePeColumnSelectOH = out Vec(Bool(),PeConfig().peColumnNum)
    val dispatchToEdgePorts = Vec(master(Stream(AxiMemControllerPort(DispatcherConfig().size))), PeConfig().peColumnNum)
    val dispatchToedgeIndexPorts = Vec(master Stream(AxiEdgeIndexPort(DispatcherConfig().edgeIndexSize)),PeConfig().peColumnNum)

    //2 switch regs port
    val dispatchVexRegOut4Gather = master(Flow(AxiMemControllerPort(DispatcherConfig().size)))

    //
    val bigLineSwitchFlag = out Bool()

    // write back ports from PE
    val writeback_valid     = in Bool()
    val writeback_payload   = in Bits(DispatcherConfig().size bits)


  }

  noIoPrefix()

  addPrePopTask(() => {
    io.flattenForeach(port => {
      val analysier = new DataAnalyzer(port)
      if (port.isInput & analysier.getFanOut.isEmpty) {
        //println(s"input port ${port.getDisplayName()} not used assign it to a register and add nopurne attribute")
        val port_reg = RegNext(port) setName (s"${port.getDisplayName()}_reg") addAttribute ("noprune")
      }

      if (port.isOutput & analysier.getFanIn.isEmpty) {
        //println(s"output port ${port.getDisplayName()} has no driver, now drived it to ø and add preserve attribute")
        port.setAsReg() // notice that there will be an output reg if there is no initial drives
        port assignFromBits (B(0, port.getBitsWidth bits))
        port.addAttribute("preserve")
      }
    })
  })

  //  axiRename(io.axiMemControlPort, "M_AXI_")
  //  axiRename(io.axiEdgeIndexPort, "M_AXI_")

  def axiRename(axi: Axi4, prefix: String): Unit = {
    axi.flattenForeach { bt =>
      val names = bt.getName().split("_")
      val channelName = names(1)
      val signalName = names.last
      val newName = (channelName ++ signalName).toUpperCase
      bt.setName(prefix ++ newName)
    }
  }

  //***************************************** axi4 edge index port assignment here  ***************************************************//
  //aw channel
  io.axiEdgeIndexPort.aw.payload.prot := B"3'b000"
  io.axiEdgeIndexPort.aw.payload.size := U"3'b100" //100 means that the 16 bytes in a transfer

  //w channel
  io.axiEdgeIndexPort.w.strb := B"16'hffff"

  //b channel

  //ar channel
  io.axiEdgeIndexPort.ar.payload.prot := B"3'b000"
  io.axiEdgeIndexPort.ar.payload.size := U"3'b100" //16 bytes(128b) in a transfer
  io.axiEdgeIndexPort.ar.payload.burst := B"2'b01" //incr type
  io.axiEdgeIndexPort.ar.len := U"8'b0000_0000"
  io.axiEdgeIndexPort.ar.valid := False
  io.axiEdgeIndexPort.ar.payload.addr := 0
  //io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst

  //  r channel
  //    io.axiMemControlPort.r.ready := False


  //************************************* axi4 port assignment here  *********************************//
  //aw channel
  io.axiMemControlPort.aw.payload.prot := B"3'b000"
  io.axiMemControlPort.aw.payload.size := U"3'b110" //110 means that the 64 bytes in a transfer

  //w channel
  io.axiMemControlPort.w.strb := B"64'hffff_ffff_ffff_ffff"

  //b channel

  //ar channel
  io.axiMemControlPort.ar.payload.prot := B"3'b000"
  io.axiMemControlPort.ar.payload.size := U"3'b110" //64 bytes(512b) in a transfer
  io.axiMemControlPort.ar.payload.burst := B"2'b01" //incr type
  io.axiMemControlPort.ar.len := U"8'b0000_0000"
  io.axiMemControlPort.ar.valid := False
  io.axiMemControlPort.ar.payload.addr := 0
  //  io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst

  //r channel
  //  io.axiMemControlPort.r.ready := False

  //*************************** AXI4 Read Data to Dispatch Stream Data *********************************//
  //vex or edge demux to different 2 stream channels
  val vexEdgeSelect = Reg(UInt(log2Up(2) bits)) init 0
  val vexEdgeOutStreams = StreamDemux(io.axiMemControlPort.r, vexEdgeSelect, 2)
  vexEdgeOutStreams(0).ready := False
  vexEdgeOutStreams(1).ready := False

  //******************************** VEX DISPATCH ********************************************************

  //vexSwitchRegOutPorts Connection
  val vexSwitchRegOutSelect = Reg(UInt(log2Up(2) bits)) init 0
  // only need one port, leaving the allocation function to PE section
  io.dispatchVexRegOut4Gather.payload.data := vexEdgeOutStreams(0).payload.data
  io.dispatchVexRegOut4Gather.valid := vexEdgeOutStreams(0).valid

  //dispatchToVexRegFilePorts Connection
  val vexPeColumnSelect = Reg(UInt(log2Up(4) bits)) init 0
  for(i <- 0 until PeConfig().peColumnNum){
    io.dispatchToVexRegFilePorts(i).payload.data := vexEdgeOutStreams(0).payload.data
    io.dispatchToVexRegFilePorts(i).valid := vexEdgeOutStreams(0).valid &&  vexPeColumnSelect === i
  }

  //******************************** EDGE INDEX DISPATCH *********************************************//
  val edgeIndexCacheFifo = StreamFifo(
    dataType = AxiEdgeIndexPort(DispatcherConfig().edgeIndexSize),
    depth = 4096
    //    pushClock = clockA,
    //    popClock = clockB
  )
  edgeIndexCacheFifo.io.push.payload.data := io.axiEdgeIndexPort.r.payload.data
  edgeIndexCacheFifo.io.push.valid := False

  //********************************** EDGE DATA DISPATCH ********************************************//
  //InFlag is from the perspective of the input of cacheFifo
  //val allZeroInFlag = vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(0) === 0 && //&&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(1) === 0 && //&&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(2) === 0 && //&&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(3) === 0 && //&&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(4) === 0 && //&&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(5) === 0 &&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(6) === 0 &&
  //  vexIndexEdgeOutStreams(1).payload.data.subdivideIn(16 bits)(7) === 0

  val allZeroIn = vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(0) === 0 &&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(1) === 0 &&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(2) === 0 &&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(3) === 0


  val seperatorIn = Bool()

  seperatorIn := vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(0) === 0 || //&&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(1) === 0 || //&&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(2) === 0 || //&&
    vexEdgeOutStreams(1).payload.data.subdivideIn(PeConfig().peColumnWid bits)(3) === 0  //&&

  val seperatorInDly = Reg(Bool()) init False
  when(seperatorIn === True){
    seperatorInDly := True
  }

  //  val allZeroInFlagReg = Reg(Bool()) init False
  val edgeCacheFifo = StreamFifo(
    dataType = AxiMemControllerPort(DispatcherConfig().size),
    depth = 4096
    //    pushClock = clockA,
    //    popClock = clockB
  )
  edgeCacheFifo.io.push.payload.data := vexEdgeOutStreams(1).payload.data
  edgeCacheFifo.io.push.valid := False


  //  val edgeCacheFifoOutRegDly1 = RegNextWhen(edgeCacheFifo.io.pop.payload.data, edgeCacheFifo.io.pop.valid)
  //  val edgeCacheFifoOutRegDly2 = RegNextWhen(edgeCacheFifoOutRegDly1,edgeCacheFifo.io.pop.valid)

  //  val onesNum = UInt(10 bits)
  //  onesNum := 0
  //  when(edgeCacheFifo.io.pop.valid){
  //    onesNum := CountOne(edgeIndexFifo.io.pop.payload.data.asBits)
  //  }

  //  val allZeroOutFlag = edgeCacheFifo.io.pop.payload.data === 0
  //  edgeCacheFifo.io.pop.valid := True
  //  val dipatchOutSeperator = Bool()

  val seperatorOut = (edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(0) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(1) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(2) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(3) === 0 ) &&
    edgeCacheFifo.io.pop.valid

  val seperatorOutDly = Reg(Bool()) init False
  when(seperatorOut === True) {
    seperatorOutDly := True
  }

  //  when(edgeCacheFifo.io.pop.valid === False){
  //    seperatorOutDly := False
  //  }

  //edge data dispatch to 4 pe column stream,stream0 for vex, stream1 for edges
  val edgePeColumnSelect = Reg(UInt(log2Up(4) bits)) init 0
  val edgePeColumnSelectOH = B"1" << edgePeColumnSelect
  for(i <- 0 until PeConfig().peColumnNum){
    io.edgePeColumnSelectOH(i) := edgePeColumnSelectOH(i)
  }

  //edgeIndexColumnOutStreams
  val edgeIndexPeColumnOutStreams = StreamDemux(edgeIndexCacheFifo.io.pop, edgePeColumnSelect, 4)
  for (i <- 0 until PeConfig().peColumnNum) { //i for ith column
    io.dispatchToedgeIndexPorts(i).payload.data := edgeIndexPeColumnOutStreams(i).payload.data
    //    when(seperatorOutDly){
    //      io.dispatchToedgeIndexPorts(i).valid := False
    //    }.otherwise{
    //      io.dispatchToedgeIndexPorts(i).valid := edgeIndexPeColumnOutStreams(i).valid
    //    }
    edgeIndexPeColumnOutStreams(i).ready := io.edgeFifoReadyVec(i)
  }

  //edgeColumnOutStreams
  val edgePeColumnOutStreams = StreamDemux(edgeCacheFifo.io.pop, edgePeColumnSelect, PeConfig().peColumnNum)
  for (i <- 0 until PeConfig().peColumnNum) { //i for ith column
    io.dispatchToEdgePorts(i).payload.data := edgePeColumnOutStreams(i).payload.data
    edgePeColumnOutStreams(i).ready := io.edgeFifoReadyVec(i)
    io.dispatchToEdgePorts(i).valid := edgePeColumnOutStreams(i).valid && edgeCacheFifo.io.pop.ready

    when(seperatorOutDly){
      edgePeColumnOutStreams(i).ready := False
    }.otherwise{
      edgePeColumnOutStreams(i).ready := io.edgeFifoReadyVec(i)
    }
  }

  val vexPeColumnNumFifo = StreamFifo(
    dataType = VexPeColumnNumFifoPort(DispatcherConfig().vexPeColumnNumFifoWidth),
    depth = 16
  )
  vexPeColumnNumFifo.io.push.valid := False
  vexPeColumnNumFifo.io.pop.ready := True
  vexPeColumnNumFifo.io.push.payload.data := vexPeColumnSelect

  //********************************  FSM Control  *******************************************************//

  val axi4MemCtrlFsm = new StateMachine {
    //FSM Internal Variables
    val axiReadVertexCnt = Reg(UInt(8 bits)) init 0
    val edgeAddrCnt = Reg(UInt(16 bits)) init 0
    //endlineflag Control

    val endLineFlag = Reg(Bool()) init False
    val peColumnSelectInOrderCnt = Reg(UInt(2 bits)) init 0

    //******************************* READ_IDLE ***********************************//
    val READ_IDLE = new State with EntryPoint {
      whenIsActive{
        goto(READ_VEX_ADDR)
      }
      onExit{
        vexPeColumnNumFifo.io.push.valid := True
      }
    }

    //******************************* READ_VEX_ADDR *********************************//
    val READ_VEX_ADDR: State = new State {

      whenIsActive {
        io.axiMemControlPort.ar.payload.len := U"8'b0000_0001" // (1+1) transfer in a burst
        io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000"

        when(io.bigPeBusyFlagVec(0) === False) {
          vexPeColumnSelect := 0
          io.axiMemControlPort.ar.valid := True
          when(io.axiMemControlPort.ar.ready) {
            goto(READ_VEX_DATA_SEND_EDGE_ADDR)
          }
        }
      }   //end of whenIsActive

      onExit{

      }
    } //end of new state

    //******************************* READ_VEX_DATA_SEND_EDGE_ADDR *********************************//
    val dispatchVexCnt = Reg(UInt(16 bits)) init 0

    val READ_VEX_DATA_SEND_EDGE_ADDR: State = new State {

      onEntry{
        vexEdgeSelect := 0
      }

      whenIsActive {

        io.axiMemControlPort.r.ready := True
        vexEdgeOutStreams(0).ready := True
        io.dispatchToVexRegFilePorts(vexPeColumnSelect).valid := True

        when(io.axiMemControlPort.r.fire) {
          axiReadVertexCnt := axiReadVertexCnt + 1
          when(io.axiMemControlPort.r.last === True) {
            axiReadVertexCnt := 0
            goto(READ_EDGE_DATA_SEND_VEX_ADDR)
          }
        }

        //send edge addr
        //axiEdgeIndexPort for Edge Index
        io.axiEdgeIndexPort.ar.valid := True
        io.axiEdgeIndexPort.ar.payload.len := U"8'b0000_1111" //burst length = 15 + 1
        io.axiEdgeIndexPort.ar.payload.addr := U"32'h00400000" + (16 * 16) * edgeAddrCnt

        // axiMemControlPort for Edge Data
        io.axiMemControlPort.ar.valid := True
        io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" //burst length = 7 + 1
        io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (8 * 64) * edgeAddrCnt
        when(io.axiMemControlPort.ar.fire){
          edgeAddrCnt := edgeAddrCnt + 1
        }
      }

      onExit {
        edgePeColumnSelect := vexPeColumnSelect
        seperatorOutDly := False
        //        bigLineDetectCnt := 0

      }
    } //end of READ_VEX_DATA_SEND_EDGE_ADDR state



    //******************************* READ_EDGE_DATA_SEND_VEX_ADDR *********************************//
    val peZeroCntVec = Vec(Reg(UInt(16 bits)) init 0, PeConfig().peNumEachColumn)
    val arFireDly = Reg(Bool()) init False

    when(io.axiMemControlPort.ar.fire){
      arFireDly := True
    }

    val bigLineDetectCnt = Reg(UInt(1 bits)) init 0
    val edgeTransferCnt = Reg(UInt(8 bits)) init 0
    val vexAddrCnt = Reg(UInt(16 bits)) init 0
    val bigLineSwitchFlag = Bool()
    bigLineSwitchFlag := False
    io.bigLineSwitchFlag := bigLineSwitchFlag

    //    val edgeTransferCnt = Reg(UInt(8 bits)) init 0

    val READ_EDGE_DATA_SEND_VEX_ADDR: State = new State {

      onEntry{
        vexEdgeSelect := 1
        seperatorInDly := False
        //        when(seperatorIn){
        //          bigLineDetectCnt := bigLineDetectCnt + 1
        //        }
      }

      whenIsActive {
        io.axiMemControlPort.r.ready := True
        edgeCacheFifo.io.push.valid := vexEdgeOutStreams(1).valid
        edgeTransferCnt := edgeTransferCnt + 1

        when(seperatorIn === True){
          bigLineDetectCnt := bigLineDetectCnt + 1
        }.otherwise{
          bigLineDetectCnt := 0
        }

        bigLineSwitchFlag := (bigLineDetectCnt === 1 && allZeroIn === True)

        when(bigLineDetectCnt === 1 && allZeroIn === True && edgeTransferCnt === 0){

          edgeIndexCacheFifo.io.push.valid := False
          edgeCacheFifo.io.push.valid := False
        }
        //          .otherwise{
        //          edgeIndexCacheFifo.io.push.valid := io.axiEdgeIndexPort.r.valid
        //          edgeCacheFifo.io.push.valid := vexEdgeOutStreams(1).valid
        //        }

        //dispatch edge data
        //io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (32*64) * edgeAddrCnt
        when((seperatorInDly||seperatorIn) && io.axiMemControlPort.r.last){
          //send vex addr
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0001" // (1+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000"
          io.axiMemControlPort.ar.valid := True
          when(io.bigPeBusyFlagVec(0) === False) {
            vexPeColumnSelect := 0
            io.axiMemControlPort.ar.valid := True
            //            vexPeColumnNumFifo.io.push.valid := True
          }.elsewhen(io.bigPeBusyFlagVec(1) === False){
            vexPeColumnSelect := 1
            io.axiMemControlPort.ar.valid := True
            vexPeColumnNumFifo.io.push.valid := True
          }.elsewhen(io.bigPeBusyFlagVec(2) === False){
            vexPeColumnSelect := 2
            io.axiMemControlPort.ar.valid := True
            //            vexPeColumnNumFifo.io.push.valid := True
          }.elsewhen(io.bigPeBusyFlagVec(3) === False){
            vexPeColumnSelect := 3
            io.axiMemControlPort.ar.valid := True
            //            vexPeColumnNumFifo.io.push.valid := True
          }
          when(io.axiMemControlPort.ar.fire){
            vexAddrCnt := vexAddrCnt + 1
          }
          goto(READ_VEX_DATA_SEND_EDGE_ADDR)
        }.elsewhen((seperatorInDly||seperatorIn) =/= True && io.axiMemControlPort.r.last){
          //send edge addr
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (8 * 64) * edgeAddrCnt
          io.axiMemControlPort.ar.valid := True
          when(io.axiMemControlPort.ar.fire){
            edgeAddrCnt := edgeAddrCnt + 1
          }
          goto(READ_EDGE_DATA_SEND_VEX_ADDR)
        }

        when(vexAddrCnt === 128) {
          goto(End)
        }.elsewhen(io.axiMemControlPort.r.last) {

        }
      } //end of whenIsActive

      onExit{
        //        seperatorInDly := False

        edgeTransferCnt := 0

        when(seperatorIn || seperatorInDly){

        }

      }

    }// end of READ_EDGE_DATA_SEND_VEX_ADDR state

    val End:State = new State {

      whenIsActive{

      }
    }

  } //end of FSM

  //************************** ScheduleBoard for Read and Write ***************************//
  case class ScheduleBoardPort() extends Bundle {
    val entryNum = UInt(4 bits)
    val busy = Bool()
    val colNum = UInt(4 bits)
  }

  val scheduleBoard = new Area {

    val scheduleBoardPorts = Vec(slave Stream (ScheduleBoardPort()), 4)
    val streamPortsArbiter = StreamArbiterFactory.roundRobin.onArgs(scheduleBoardPorts(0), scheduleBoardPorts(1), scheduleBoardPorts(2), scheduleBoardPorts(3))
    streamPortsArbiter.ready := True

    val entry = new Bundle {
      val busy = Reg(UInt(1 bits)) init 0
      val colNum = Reg(UInt(2 bits)) init 0
    }

    noIoPrefix()

    val scheduleBoard = Vec(entry, 4)

    def writeField(entryNum: Int, fieldName: String, value: Int) = {
      fieldName match {
        case "busy" => scheduleBoard(entryNum).busy := U(value)
        case "colNum" => scheduleBoard(entryNum).colNum := U(value)
        case _ => report("Wrong field name")
      }
    }

    def readField(entryNum: Int, fieldName: String) = {
      fieldName match {
        case "busy" => scheduleBoard(entryNum).busy
        case "colNum" => scheduleBoard(entryNum).colNum
        case _ => report("Wrong field name")
      }
    }
  } //end of scheduleboard

}//end of component
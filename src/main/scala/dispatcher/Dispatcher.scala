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

case class VexPeColumnNumFifoPort(dataWidth:Int) extends Bundle {
  val data = UInt(DispatcherConfig().vexPeColumnNumFifoWidth bits)
}

case class StreamFifoPort(dataWidth:Int) extends Bundle{
  val data = Bits(DispatcherConfig().fifoWidth bits)
}
case class Dispatcher() extends Component {

  val axiConfig = Axi4Config(addressWidth = AxiConfig().addrWid, dataWidth = AxiConfig().dataWid, idWidth = AxiConfig().idWid)

  val io = new Bundle {

    // MEM
    val axiMemControlPort = master(Axi4(axiConfig))

    // to PE
    val read_flag = in Bool()
    val result = out Bool()
    val bigPeBusyFlagVec = in Vec(Bool(),PeConfig().peColumnNum)
    val edgeFifoReadyVec = in Vec(Bool(),PeConfig().peColumnNum)

    val RB_switch = out Bool()
    val vex2pe  = Vec(master(Flow(AxiMemControllerPort(DispatcherConfig().size))), PeConfig().peColumnNum)
    val edge2pe = Vec(master(Stream(AxiMemControllerPort(DispatcherConfig().size))), PeConfig().peColumnNum)
    val edgePeColumnSelectOH = out Vec(Bool(),PeConfig().peColumnNum)
    
    // GE
    val vex2ge = master(Flow(AxiMemControllerPort(DispatcherConfig().size)))
    val writeback_valid         = in Bool()
    val writeback_payload       = in Bits(DispatcherConfig().size bits)

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


  //*************************** AXI4 Read Data to Dispatch Stream Data *********************************//
  //vex or edge demux to different 2 stream channels
  val vexEdgeSelect = Reg(UInt(log2Up(2) bits)) init 0
  val vexEdgeOutStreams = StreamDemux(io.axiMemControlPort.r, vexEdgeSelect, 2)
  vexEdgeOutStreams(0).ready := False
  vexEdgeOutStreams(1).ready := False

  //******************************** VEX DISPATCH ********************************************************

  val vexSwitchRegOutSelect = Reg(UInt(log2Up(2) bits)) init 0
  // only need one port, leaving the allocation function to PE section
  io.vex2ge.payload.data := vexEdgeOutStreams(0).payload.data
  io.vex2ge.valid := False

  val vexPeColumnSelect = Reg(UInt(log2Up(4) bits)) init 0
  for(i <- 0 until PeConfig().peColumnNum){
    io.vex2pe(i).payload.data := vexEdgeOutStreams(0).payload.data
    io.vex2pe(i).valid := vexEdgeOutStreams(0).valid &&  vexPeColumnSelect === i
  }

  //********************************** EDGE DATA DISPATCH ********************************************//
  //InFlag is from the perspective of the input of cacheFifo
  val select0th128bPacketFlag = (vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(0).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                             vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(0).subdivideIn(8 bits)(1) =/= 0x00)
  val select1st128bPacketFlag = (vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(1).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                             vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(1).subdivideIn(8 bits)(1) =/= 0x00)
  val select2nd128bPacketFlag = (vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(2).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                             vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(2).subdivideIn(8 bits)(1) =/= 0x00)
  val select3rd128bPacketFlag = (vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(3).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                             vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(3).subdivideIn(8 bits)(1) =/= 0x00)
  val headerInFlag =  select0th128bPacketFlag || select1st128bPacketFlag || select2nd128bPacketFlag || select3rd128bPacketFlag

  val select128bPacketIn = UInt(2 bits)
  select128bPacketIn := 0
  when(select0th128bPacketFlag){
    select128bPacketIn := 0
  }.elsewhen(select1st128bPacketFlag){
    select128bPacketIn := 1
  }.elsewhen(select2nd128bPacketFlag){
    select128bPacketIn := 2
  }.elsewhen(select3rd128bPacketFlag){
    select128bPacketIn := 3
  }

  val edgeIndexIn = Reg(UInt(8 bits)) init 0
  val bigLineJumpStepIn = Reg(UInt(8 bits)) init 0
  val jumpStepIn = Reg(UInt(8 bits)) init 0
  when(headerInFlag){
    edgeIndexIn := vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(1).asUInt
    bigLineJumpStepIn := vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(2).asUInt
    jumpStepIn := vexEdgeOutStreams(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(3).asUInt
  }


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
  )
  edgeCacheFifo.io.push.payload.data := vexEdgeOutStreams(1).payload.data
  edgeCacheFifo.io.push.valid := False

  val select0th128bPacketOutFlag = (edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(0).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                                    edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(0).subdivideIn(8 bits)(1) =/= 0x00)
  val select1st128bPacketOutFlag = (edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(1).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                                    edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(1).subdivideIn(8 bits)(1) =/= 0x00)
  val select2nd128bPacketOutFlag = (edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(2).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                                    edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(2).subdivideIn(8 bits)(1) =/= 0x00)
  val select3rd128bPacketOutFlag = (edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(3).subdivideIn(8 bits)(0) === 0x00 &&  //&&
                                    edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(3).subdivideIn(8 bits)(1) =/= 0x00)
  val headerOutFlag =  select0th128bPacketOutFlag || select1st128bPacketOutFlag || select2nd128bPacketOutFlag || select3rd128bPacketOutFlag

  val select128bPacketOut = UInt(2 bits)
  select128bPacketOut := 0
  when(select0th128bPacketOutFlag){
    select128bPacketOut := 0
  }.elsewhen(select1st128bPacketOutFlag){
    select128bPacketOut := 1
  }.elsewhen(select2nd128bPacketOutFlag){
    select128bPacketOut := 2
  }.elsewhen(select3rd128bPacketOutFlag){
    select128bPacketOut := 3
  }

  val edgeIndexOut = Reg(UInt(8 bits)) init 0
  val bigLineJumpStepOut = Reg(UInt(8 bits)) init 0
  val jumpStepOut = Reg(UInt(8 bits)) init 0
  when(headerOutFlag){
    edgeIndexOut := edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(select128bPacketOut).subdivideIn(8 bits)(1).asUInt
    bigLineJumpStepOut := edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(select128bPacketOut).subdivideIn(8 bits)(2).asUInt
    jumpStepOut := edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(select128bPacketOut).subdivideIn(8 bits)(3).asUInt
  }

  io.RB_switch := False
  when(bigLineJumpStepOut > 0){
    io.RB_switch := True
  }

  val seperatorOut = (edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(0) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(1) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(2) === 0 ||
    edgeCacheFifo.io.pop.payload.data.subdivideIn(PeConfig().peColumnWid bits)(3) === 0 ) &&
    edgeCacheFifo.io.pop.valid

  val seperatorOutDly = Reg(Bool()) init False
  when(seperatorOut === True) {
    seperatorOutDly := True
  }

  //edge data dispatch to 4 pe column stream,stream0 for vex, stream1 for edges
  val edgePeColumnSelect = Reg(UInt(log2Up(4) bits)) init 0
  val edgePeColumnSelectOH = B"1" << edgePeColumnSelect
  for(i <- 0 until PeConfig().peColumnNum){
    io.edgePeColumnSelectOH(i) := edgePeColumnSelectOH(i)
  }

  //edgeColumnOutStreams
  val edgePeColumnOutStreams = StreamDemux(edgeCacheFifo.io.pop, edgePeColumnSelect, PeConfig().peColumnNum)
  for (i <- 0 until PeConfig().peColumnNum) { //i for ith column
    io.edge2pe(i).payload.data := edgePeColumnOutStreams(i).payload.data
    edgePeColumnOutStreams(i).ready := io.edgeFifoReadyVec(i)
    io.edge2pe(i).valid := edgePeColumnOutStreams(i).valid && edgeCacheFifo.io.pop.ready

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

    // test code, no usage
    val testCnt = Reg(UInt(8 bits)) init 0
    when(io.read_flag){
      testCnt := testCnt + 1
    }

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
        io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000" + (2 * 64) * DispatcherConfig().initCol

        when(io.bigPeBusyFlagVec(0) === False) {
          vexPeColumnSelect := 0
          io.axiMemControlPort.ar.valid := True
          when(io.axiMemControlPort.ar.ready) {
            goto(READ_VEX_DATA_SEND_EDGE_ADDR)
          }
        }
      }  //end of whenIsActive

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
        io.vex2pe(vexPeColumnSelect).valid := True

        when(io.axiMemControlPort.r.fire) {
          axiReadVertexCnt := axiReadVertexCnt + 1
          when(io.axiMemControlPort.r.last === True) {
            axiReadVertexCnt := 0
            goto(READ_EDGE_DATA_SEND_VEX_ADDR)
          }
        }

        when(io.axiMemControlPort.r.last){
          io.axiMemControlPort.ar.valid := True
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" //burst length = 7 + 1
          io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (8 * 64) * edgeAddrCnt
          when(io.axiMemControlPort.ar.fire){
            edgeAddrCnt := edgeAddrCnt + 1
          }
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
    val RB_switch = Bool()
    RB_switch := False
    val RB_switch_dly = Reg(Bool()) init False
    when(RB_switch === True){
      RB_switch_dly := True
    }

    val READ_EDGE_DATA_SEND_VEX_ADDR: State = new State {

      onEntry{
        vexEdgeSelect := 1
        seperatorInDly := False
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

        RB_switch := (bigLineDetectCnt === 1 && allZeroIn === True)

        when(bigLineDetectCnt === 1 && allZeroIn === True && edgeTransferCnt === 0){
          edgeCacheFifo.io.push.valid := False
        }
        //dispatch edge data
        //io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (32*64) * edgeAddrCnt
        when((RB_switch || RB_switch_dly) && io.axiMemControlPort.r.last){

          //send vex addr for gather
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (1+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000"
          io.axiMemControlPort.ar.valid := True
          vexEdgeSelect := 0
          io.vex2ge.valid := vexEdgeOutStreams(0).valid

          goto(READ_VEX_DATA_FOR_GATHER_SEND_EDGE_ADDR)

        }.elsewhen((seperatorInDly||seperatorIn) && io.axiMemControlPort.r.last){
          //send vex addr
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0001" // (1+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000" + (2*64) * jumpStepIn
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
            goto(READ_VEX_DATA_SEND_EDGE_ADDR)
          }

        }.elsewhen((seperatorInDly||seperatorIn) =/= True && io.axiMemControlPort.r.last){

          io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (8 * 64) * edgeAddrCnt
          io.axiMemControlPort.ar.valid := True
          when(io.axiMemControlPort.ar.fire){
            edgeAddrCnt := edgeAddrCnt + 1
          }
          goto(READ_EDGE_DATA_SEND_VEX_ADDR)
        }

        when(edgeAddrCnt === 128){
          io.axiMemControlPort.aw.payload.addr := U"32'h00800000"
          io.axiMemControlPort.aw.valid := io.writeback_valid
          io.axiMemControlPort.w.payload.data := io.writeback_payload
        }
      } //end of whenIsActive

      onExit{
        //        seperatorInDly := False
        edgeTransferCnt := 0
      }

    }// end of READ_EDGE_DATA_SEND_VEX_ADDR state

    val READ_VEX_DATA_FOR_GATHER_SEND_EDGE_ADDR :State = new State {

      whenIsActive{
        io.axiMemControlPort.r.ready := True
        io.vex2ge.valid := True

        when(io.axiMemControlPort.r.last){
          //send edge addr
          io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst
          io.axiMemControlPort.ar.payload.addr := U"32'h00800000" + (8 * 64) * edgeAddrCnt
          io.axiMemControlPort.ar.valid := True
          when(io.axiMemControlPort.ar.fire){
            edgeAddrCnt := edgeAddrCnt + 1
          }
          goto(READ_EDGE_DATA_SEND_VEX_ADDR)
        }
      }
      onExit{
        RB_switch_dly := False
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
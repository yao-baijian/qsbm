package dispatcher
import spinal.core.Component.push
import spinal.core._
import spinal.lib._
import spinal.lib.{Stream, _}
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.tools.DataAnalyzer
import spinal.lib.fsm._
import PE.qsbConfig

case class AxiMemControllerPort(dataWidth:Int) extends Bundle {
  val data = Bits(Config().axi_width bits)
  // def isBlack : Bool = red === 0 && green === 0 && blue === 0
}

case class StreamFifoPort(dataWidth:Int) extends Bundle{
  val data = Bits(Config().axi_width bits)
}
case class Dispatcher(config: Config) extends Component {

  val axiConfig = Axi4Config(addressWidth = config.addrWid, 
    							dataWidth = config.axi_width, 
								idWidth = config.idWid)

  val io = new Bundle {

    // MEM
    val axiMemControlPort = master(Axi4(axiConfig))
	// Config Space
	  val qsb_cfg 	= slave(qsbConfig())
    val start 		= in Bool()
    val done 		  = out Bool()
    val srst   		= in Bool()
    val vex_a_base= in UInt(32 bits)
    val vex_b_base= in UInt(32 bits)
    val edge_base = in UInt(32 bits)
    // to PE
    val pe_busy 	= in Vec(Bool(), config.pe_num)
    val pe_rdy    = in Vec(Bool(), config.pe_num)
    val RB_switch = out Bool()
    val vex2pe  	= Vec(master(Flow(AxiMemControllerPort(config.axi_width))), config.pe_num)
    val edge2pe 	= Vec(master(Stream(AxiMemControllerPort(config.axi_width))), config.pe_num)
    // GE
    val vex2ge 		= master(Flow(AxiMemControllerPort(config.axi_width)))
    val wb_valid  = in Bool()
    val wb_payload= in Bits(config.axi_width bits)

  }

  noIoPrefix()

  addPrePopTask(() => {
    io.flattenForeach(port => {
      val analysier = new DataAnalyzer(port)
      if (port.isInput & analysier.getFanOut.isEmpty) {
        val port_reg = RegNext(port) setName (s"${port.getDisplayName()}_reg") addAttribute ("noprune")
      }

      if (port.isOutput & analysier.getFanIn.isEmpty) {
        port.setAsReg() // notice that there will be an output reg if there is no initial drives
        port assignFromBits (B(0, port.getBitsWidth bits))
        port.addAttribute("preserve")
      }
    })
  })

    io.axiMemControlPort.aw.payload.prot 	:= B"3'b000"
    io.axiMemControlPort.aw.payload.size 	:= U"3'b110" //110 means that the 64 bytes in a transfer
    //w channel
    io.axiMemControlPort.w.strb 			:= B"64'hffff_ffff_ffff_ffff"
    //ar channel
    io.axiMemControlPort.ar.payload.prot 	:= B"3'b000"
    io.axiMemControlPort.ar.payload.size 	:= U"3'b110" //64 bytes(512b) in a transfer
    io.axiMemControlPort.ar.payload.burst   := B"2'b01" //incr type
    io.axiMemControlPort.ar.len 			:= U"8'b0000_0000"
    io.axiMemControlPort.ar.valid 		    := False
    io.axiMemControlPort.ar.payload.addr 	:= 0
    //  io.axiMemControlPort.ar.payload.len := U"8'b0000_0111" // (7+1) transfer in a burst


    val vexEdgeSelect       = Reg(UInt(log2Up(2) bits)) init 0
    val data_stream         = StreamDemux(io.axiMemControlPort.r, vexEdgeSelect, 2)
    val vexPeColumnSelect   = Reg(UInt(log2Up(4) bits)) init 0
    val pe_select           = Reg(UInt(log2Up(4) bits)) init 0
    val vexSwitchRegOutSelect = Reg(UInt(log2Up(2) bits)) init 0

    val itr_cnt             = Reg(UInt(10 bits)) init 0
    val switch              = Reg(Bool()) init True
    val vex_base_addr       = UInt(32 bits)
    val vex_base_addr_r     = UInt(32 bits)

    vex_base_addr   := Mux(switch, io.vex_a_base, io.vex_b_base)
    vex_base_addr_r := Mux(!switch, io.vex_a_base, io.vex_b_base)

    io.vex2ge.payload.data := data_stream(0).payload.data
    io.vex2ge.valid := False

    data_stream(0).ready := False
    data_stream(1).ready := False

    for(i <- 0 until config.pe_num){
        io.vex2pe(i).payload.data := data_stream(0).payload.data
        io.vex2pe(i).valid := data_stream(0).valid && vexPeColumnSelect === i
    }

    io.axiMemControlPort.aw.payload.addr    := vex_base_addr_r
    io.axiMemControlPort.aw.valid           := io.wb_valid
    io.axiMemControlPort.w.payload.data     := io.wb_payload
    

    //********************************** EDGE DATA DISPATCH ********************************************//
  	val header		= Vec(Bool(), 4)
	val single_zero = Vec(Bool(), 4)

	for (i <- 0 until 4) {
		single_zero(i) 	:= (data_stream(1).payload.data.subdivideIn(128 bits)(i) === 0)
		header(i) 		:= (data_stream(1).payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(0) === 0x00 &&  
							data_stream(1).payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(1) =/= 0x00)
	}

	val all_zero        = single_zero.andR
	val partial_zero    = single_zero.orR
	val header_in 		= header.orR
  	val partial_zero_r  = Reg(Bool()) init False
	val tile_idx 		= Reg(UInt(8 bits)) init 0
	val RB_incr 		= Reg(UInt(8 bits)) init 0
	val CB_incr 		= Reg(UInt(8 bits)) init 0

	when(partial_zero === True){
		partial_zero_r := True
	}
	
	val select128bPacketIn = UInt(2 bits)
	select128bPacketIn := 0

	when(single_zero(0)){
		select128bPacketIn := 0
	}.elsewhen(single_zero(1)) {
		select128bPacketIn := 1
	}.elsewhen(single_zero(2)) {
		select128bPacketIn := 2
	}.elsewhen(single_zero(3)) {
		select128bPacketIn := 3
	}

	when(header_in){
		tile_idx 	:= data_stream(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(1).asUInt
		RB_incr 	:= data_stream(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(2).asUInt
		CB_incr 	:= data_stream(1).payload.data.subdivideIn(128 bits)(select128bPacketIn).subdivideIn(8 bits)(3).asUInt
	}

	val edgeCacheFifo = StreamFifo(
		dataType = AxiMemControllerPort(config.axi_width),
		depth = 128
	)
    val edgePeColumnOutStreams = StreamDemux(edgeCacheFifo.io.pop, pe_select, config.pe_num)

	edgeCacheFifo.io.push.payload.data := data_stream(1).payload.data
	edgeCacheFifo.io.push.valid := False

	io.RB_switch := False
	when(RB_incr > 0) {
		io.RB_switch := True
	}

  	val all_zero_out = Vec(Bool(), 4)
	val all_zero_out_r = Reg(Bool()) init False
	for (i <- 0 until 4) {
		all_zero_out(i) := (edgeCacheFifo.io.pop.payload.data.subdivideIn(128 bits)(i) === 0)
	}
	when(all_zero_out.orR === True && edgeCacheFifo.io.pop.valid) {
		all_zero_out_r := True
	}

    
    for (i <- 0 until config.pe_num) {
        io.edge2pe(i).payload.data := edgePeColumnOutStreams(i).payload.data
        edgePeColumnOutStreams(i).ready := io.pe_rdy(i)
        io.edge2pe(i).valid := edgePeColumnOutStreams(i).valid && edgeCacheFifo.io.pop.ready
        when(all_zero_out_r){
        edgePeColumnOutStreams(i).ready := False
        }.otherwise{
        edgePeColumnOutStreams(i).ready := io.pe_rdy(i)
        }
    }


  //********************************  FSM Control  *******************************************************//

    val axi4MemCtrlFsm = new StateMachine {

        val edge_addr   = Reg(UInt(16 bits)) init 0
        val arFireDly   = Reg(Bool()) init False
        val RB_all      = Reg(UInt(8 bits)) init 0

        when(io.axiMemControlPort.ar.fire){
        arFireDly := True
        }

        val RB_switch = Bool()
        RB_switch := False
        val RB_switch_dly = Reg(Bool()) init False
        when(RB_switch === True){
        RB_switch_dly := True
        }

        val IDLE = new State with EntryPoint {
        whenIsActive{
            when(io.start){
            goto(SEND_VEX_ADDR)
            }
        }
        }

        val SEND_VEX_ADDR: State = new State {
            whenIsActive {
                io.axiMemControlPort.ar.payload.len := U(1) // (1+1) transfer in a burst
                io.axiMemControlPort.ar.payload.addr := vex_base_addr + io.qsb_cfg.CB_init
                when(io.pe_busy.orR === False) {
                    io.axiMemControlPort.ar.valid := True
                    when(io.axiMemControlPort.ar.ready) {
                        goto(READ_VEX_DATA_SEND_EDGE_ADDR)
                    }
                }
            }
        } 

        val READ_VEX_DATA_SEND_EDGE_ADDR: State = new State {
            onEntry{
                vexEdgeSelect := 0
            }
            whenIsActive {
                io.axiMemControlPort.r.ready := True
                data_stream(0).ready := True
                io.vex2pe(vexPeColumnSelect).valid := True
                when(io.axiMemControlPort.r.last){
                    io.axiMemControlPort.ar.valid := True
                    io.axiMemControlPort.ar.payload.len := U(7) //burst length = 7 + 1
                    io.axiMemControlPort.ar.payload.addr := io.edge_base + (8 * 64) * edge_addr
                    edge_addr := edge_addr + 1
                    goto(READ_EDGE_DATA_SEND_VEX_ADDR)
                }
            }
            onExit {
                pe_select := vexPeColumnSelect
                all_zero_out_r := False
            }
        } 

        val READ_EDGE_DATA_SEND_VEX_ADDR: State = new State {

            onEntry{
                vexEdgeSelect := 1
                partial_zero_r := False
            }
            whenIsActive {
                io.axiMemControlPort.r.ready := True
                edgeCacheFifo.io.push.valid := data_stream(1).valid
                RB_switch := partial_zero_r && all_zero

                when((RB_switch || RB_switch_dly) && io.axiMemControlPort.r.last){
                    io.axiMemControlPort.ar.payload.len := U(7)
                    io.axiMemControlPort.ar.payload.addr := vex_base_addr
                    io.axiMemControlPort.ar.valid := True
                    vexEdgeSelect   := 0
                    io.vex2ge.valid := data_stream(0).valid
                    goto(READ_VEX_DATA_GE_SEND_VEX_ADDR)

                }.elsewhen(( partial_zero_r || partial_zero ) && io.axiMemControlPort.r.last){
                    io.axiMemControlPort.ar.payload.len := U"8'b0000_0001" 
                    io.axiMemControlPort.ar.payload.addr := U"32'h0000_0000" + (2*64) * CB_incr
                    io.axiMemControlPort.ar.valid := True

                    when(io.pe_busy(0) === False) {
                        vexPeColumnSelect := 0
                    }.elsewhen(io.pe_busy(1) === False){
                        vexPeColumnSelect := 1
                    }.elsewhen(io.pe_busy(2) === False){
                        vexPeColumnSelect := 2
                    }.elsewhen(io.pe_busy(3) === False){
                        vexPeColumnSelect := 3
                    }.otherwise{
                        vexPeColumnSelect := 0
                    }

                    when(io.pe_busy.andR) {
                        io.axiMemControlPort.ar.valid := False
                    } otherwise {
                        io.axiMemControlPort.ar.valid := True
                    }

                    when(io.axiMemControlPort.ar.fire){
                        goto(READ_VEX_DATA_SEND_EDGE_ADDR)
                    }
                }.elsewhen((partial_zero_r || partial_zero ) =/= True && io.axiMemControlPort.r.last){
                    io.axiMemControlPort.ar.payload.len := U(7) // (7+1) transfer in a burst
                    io.axiMemControlPort.ar.payload.addr := io.edge_base  + (8 * 64) * edge_addr
                    io.axiMemControlPort.ar.valid := True
                    when(io.axiMemControlPort.ar.fire){
                        edge_addr := edge_addr + 1
                    }
                }
            }
        }

        val READ_VEX_DATA_GE_SEND_VEX_ADDR :State = new State {
            onEntry{
                switch      := !switch
            }
            whenIsActive{
                io.axiMemControlPort.r.ready := True
                io.vex2ge.valid := True
                when(io.axiMemControlPort.r.last){
                    io.axiMemControlPort.ar.payload.len := U(1)
                    io.axiMemControlPort.ar.payload.addr := vex_base_addr + io.qsb_cfg.CB_init
                    io.axiMemControlPort.ar.valid := True
                    when(io.axiMemControlPort.r.last){
                        when (itr_cnt === 1000) {
                            goto(IDLE)  
                        } otherwise {
                            RB_all := RB_all + 1
                            when (RB_all === io.qsb_cfg.RB_max) {
                                itr_cnt     := itr_cnt + 1
                                RB_all      := 0
                                edge_addr   := 0
                            }
                            goto(READ_VEX_DATA_SEND_EDGE_ADDR)
                        }
                    }
                }
            }
            onExit{
                RB_switch_dly := False
            }
        }

    }

}//end of component
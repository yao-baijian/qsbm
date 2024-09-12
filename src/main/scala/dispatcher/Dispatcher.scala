package dispatcher
import spinal.core.Component.push
import spinal.core._
import spinal.lib._
import spinal.lib.{Stream, _}
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.tools.DataAnalyzer
import spinal.lib.fsm._
import PE.qsbConfig

case class Axi2Stream() extends Bundle {
  val data = Bits(Config().axi_width bits)
  // def isBlack : Bool = red === 0 && green === 0 && blue === 0
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
    val vex2pe  	= Vec(master(Flow(Axi2Stream())), config.pe_num)
    val edge2pe 	= Vec(master(Stream(Axi2Stream())), config.pe_num)
    // GE
    val vex2ge 		= master(Flow(Axi2Stream()))
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

  val vexEdgeSelect       = Reg(UInt(1 bits)) init 0
  val pe_select           = Reg(UInt(2 bits)) init 0
  val itr_cnt             = Reg(UInt(10 bits)) init 0
  val ping_pong           = Reg(Bool()) init True
  val vex_base_addr       = UInt(32 bits)
  val vex_base_addr_r     = UInt(32 bits)
  val partial_zero_r      = Reg(Bool()) init False

  val edgeFifo = StreamFifo(
    dataType = Axi2Stream(),
    depth = 128
  )

  val data_stream  = StreamDemux(io.axiMemControlPort.r, vexEdgeSelect, 2) // to vex, to edge
  val edge_stream  = StreamDemux(edgeFifo.io.pop, pe_select, config.pe_num)// edge to pe 1, 2, 3, 4
  val vex_stream   = StreamDemux(data_stream(0), pe_select, config.pe_num) // vex to pe 1, 2, 3, 4

  for (i <- 0 until config.pe_num) {
    io.vex2pe(i).payload.data  := vex_stream(i).payload.data
    io.vex2pe(i).valid         := vex_stream(i).valid
    vex_stream(i).ready        := True
  }

  // axi setup
  io.axiMemControlPort.aw.payload.prot 	:= B"3'b000"
  io.axiMemControlPort.aw.payload.size 	:= U"3'b110" //110 means that the 64 bytes in a transfer

  io.axiMemControlPort.w.strb 			    := B"64'hffff_ffff_ffff_ffff"
  io.axiMemControlPort.ar.payload.prot 	:= B"3'b000"
  io.axiMemControlPort.ar.payload.size 	:= U"3'b110" //64 bytes(512b) in a transfer
  io.axiMemControlPort.ar.payload.burst := B"2'b01" //incr type
  io.axiMemControlPort.ar.len 			    := U"8'b0000_0000"
  io.axiMemControlPort.ar.payload.addr 	:= 0
  io.axiMemControlPort.ar.valid         := False

  // dispatcher 2 pe
  vex_base_addr                 := Mux(ping_pong, io.vex_a_base, io.vex_b_base)
  vex_base_addr_r               := Mux(!ping_pong, io.vex_a_base, io.vex_b_base)

  data_stream(1).ready          := False
  edgeFifo.io.push.payload.data := data_stream(1).payload.data
  edgeFifo.io.push.valid        := data_stream(1).valid

  // dispatcher 2 ge
  io.vex2ge.payload.data  := data_stream(0).payload.data
  io.vex2ge.valid         := False

  io.axiMemControlPort.aw.payload.addr    := vex_base_addr_r
  io.axiMemControlPort.aw.valid           := io.wb_valid
  io.axiMemControlPort.w.payload.data     := io.wb_payload

  // edge read in, detect zero
  val single_zero         = UInt(4 bits)
  val partial_zero        = single_zero.orR
  val edge_in_ptr         = UInt(4 bits)

  for(i <- 0 until config.pe_num) {
    single_zero(i)  := (data_stream(1).payload.data.subdivideIn(128 bits)(i) === 0)
  }

  when( partial_zero === True ){
    partial_zero_r := True
  }

  switch(single_zero) {
    is (1) { edge_in_ptr := 0 }
    is (2) { edge_in_ptr := 1 }
    is (4) { edge_in_ptr := 2 }
    is (8) { edge_in_ptr := 3 }
    default { edge_in_ptr := 0 }
  }

  // edge send out, decide nxt CB, RB
  val head		            = UInt(4 bits)
  val header 		          = head.orR
  val RB_incr_nxt 		    = Reg(UInt(8 bits)) init 0
  val CB_incr_nxt 		    = Reg(UInt(8 bits)) init 0
  val header_ptr          = UInt(2 bits)

  switch(head) {
    is (1) { header_ptr := 0 }
    is (2) { header_ptr := 1 }
    is (4) { header_ptr := 2 }
    is (8) { header_ptr := 3 }
    default { header_ptr := 0 }
  }

  for(i <- 0 until config.pe_num) {
    head(i) := (edgeFifo.io.pop.payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(0) === 0x00 &&
      edgeFifo.io.pop.payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(1) =/= 0x00)
  }

  when(header){
    RB_incr_nxt 	:= edgeFifo.io.pop.payload.data.subdivideIn(128 bits)(header_ptr).subdivideIn(8 bits)(2).asUInt
    CB_incr_nxt 	:= edgeFifo.io.pop.payload.data.subdivideIn(128 bits)(header_ptr).subdivideIn(8 bits)(3).asUInt
  }

  io.RB_switch := False
  when(RB_incr_nxt > 0) {
    io.RB_switch := True
  }

  val all_zero_out    = Vec(Bool(), 4)
  val all_zero_out_r  = Reg(Bool()) init False
  for (i <- 0 until 4) {
    all_zero_out(i) := (edgeFifo.io.pop.payload.data.subdivideIn(128 bits)(i) === 0)
  }
  when(all_zero_out.orR === True && edgeFifo.io.pop.valid) {
    all_zero_out_r := True
  }
    
  for (i <- 0 until config.pe_num) {
      io.edge2pe(i).payload.data  := edge_stream(i).payload.data
      io.edge2pe(i).valid         := edge_stream(i).valid && edgeFifo.io.pop.ready
      when(all_zero_out_r){
        edge_stream(i).ready := False
      }.otherwise{
        edge_stream(i).ready := io.pe_rdy(i)
      }
  }

  // Dispatcher fsm
  val pe_nxt      = Reg(UInt(2 bits)) init 0
  val pe_nxt_rdy  = Reg(Bool()) init True

  pe_nxt_rdy := !io.pe_busy.andR

  when(!io.pe_busy(0)) { pe_nxt := 0 }
    .elsewhen(!io.pe_busy(1)){ pe_nxt := 1}
    .elsewhen(!io.pe_busy(2)){ pe_nxt := 2}
    .elsewhen(!io.pe_busy(3)){ pe_nxt := 3}
    .otherwise{ pe_nxt := 0}

  val axi4MemCtrlFsm = new StateMachine {

    val edge_addr   = Reg(UInt(16 bits)) init 0
    val RB_all      = Reg(UInt(8 bits)) init 0

    val IDLE = new State with EntryPoint {
      whenIsActive{
        when(io.start){
          goto(SEND_INIT_VEX_ADDR)
        }
      }
    }

    val SEND_INIT_VEX_ADDR: State = new State {
      whenIsActive {
        set_read(U(1), vex_base_addr + io.qsb_cfg.CB_init) // read to initial vex addr
        when(io.pe_busy.orR === False) {
          enable_read ()
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
        set_read(U(7), io.edge_base + (8 * 64) * edge_addr) // read to edge addr
        enable_read()
        data_stream(0).ready := True
        // read finish
        when(io.axiMemControlPort.r.last) {
          edge_addr := edge_addr + 1
          goto(READ_EDGE_DATA_SEND_VEX_ADDR)
        }
      }
      onExit {
          all_zero_out_r := False
      }
    }

    val READ_EDGE_DATA_SEND_VEX_ADDR: State = new State {
      onEntry{
        vexEdgeSelect := 1
        partial_zero_r := False
      }
      whenIsActive {
        when( io.RB_switch && partial_zero_r) {
          // all zero detected and RB switch, need to collect vertex for GE
          set_read(U(7), vex_base_addr)
          enable_read()   // issue read
          io.vex2ge.valid := data_stream(0).valid
          goto(READ_VEX_DATA_GE_SEND_VEX_ADDR)

        } elsewhen ( partial_zero_r ) {
          // all zero detected, transfer to read nxt vertex, it is possible that still some edge remains in edge FIFO
          // and its also possible no PE is available, the state machine jump until nxt pe is confirmed
          set_read(U(1), vex_base_addr + (2*64) * CB_incr_nxt)

          when(pe_nxt_rdy) {
            enable_read()
            get_nxt_pe()
          }
          when(io.axiMemControlPort.ar.fire) { goto(READ_VEX_DATA_SEND_EDGE_ADDR) }
        } otherwise {
          // keep read next edge
          set_read(U(7), vex_base_addr + io.edge_base  + (8 * 64) * edge_addr)
          enable_read()
          when(io.axiMemControlPort.ar.fire) { edge_addr := edge_addr + 1}
        }
      }
    }

    val READ_VEX_DATA_GE_SEND_VEX_ADDR :State = new State {
      onEntry{
        vexEdgeSelect   := 0
      }
      whenIsActive{
        // After get all vertex, need local nxt RB, CB, issue next vertex using CB increment
        io.vex2ge.valid := data_stream(0).valid
        // TODO:needfix
        // issue next vertex read
        set_read(U(1), vex_base_addr + io.qsb_cfg.CB_init)
        enable_read()
        // in last cycle,
        when(io.axiMemControlPort.r.last){
          when (itr_cnt === io.qsb_cfg.iteration) {
            goto(IDLE)
          } otherwise {
            RB_all := RB_all + 1
            when(RB_all === io.qsb_cfg.RB_max) {
              ping_pong := !ping_pong
              itr_cnt   := itr_cnt + 1
              RB_all    := 0
              edge_addr := 0
              goto(SEND_INIT_VEX_ADDR)
            }
            goto(READ_VEX_DATA_SEND_EDGE_ADDR)
          }
        }
      }
    }
  }

  def get_nxt_pe (): Unit = {
    pe_select := pe_nxt
  }

  def enable_read () = {
    io.axiMemControlPort.ar.valid := True
  }

  def set_read (len: UInt, addr: UInt) = {
    io.axiMemControlPort.ar.payload.len := len
    io.axiMemControlPort.ar.payload.addr := addr
  }

}//end of component
package dispatcher
import spinal.core.Component.push
import spinal.core._
import spinal.lib._
import spinal.lib.{Stream, _}
import spinal.lib.bus.amba4.axi.{Axi4, Axi4Config}
import spinal.lib.tools.DataAnalyzer
import spinal.lib.fsm._
import PE.qsbConfig
import spinal.core.sim.SimDataPimper

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
    val RB_switch = out Bool()
    val vex2pe  	= Vec(master(Flow(Axi2Stream())), config.pe_num)
    val edge2pe 	= Vec(master(Stream(Axi2Stream())), config.pe_num)
    val update_busy = in Bool()

    update_busy.simPublic()

    // GE
    val vex2ge 		= master(Flow(Axi2Stream()))
    val wb_valid  = in Bool()
    val wb_payload= in Bits(config.axi_width bits)
    val itr_cnt   = out UInt(16 bits)

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
  val itr_cnt             = Reg(UInt(16 bits)) init 0
  val ping_pong           = Reg(Bool()) init True
  val vex_base_addr       = UInt(32 bits)
  val vex_base_addr_r     = UInt(32 bits)
  val pe_nxt              = Reg(UInt(2 bits)) init 0
  val pe_nxt_rdy          = Reg(Bool()) init True

  val data_stream         = StreamDemux(io.axiMemControlPort.r, vexEdgeSelect, 2) // to vex, to edge
  val edge_stream         = StreamDemux(data_stream(1), pe_select, config.pe_num) // edge to pe 1, 2, 3, 4
  val vex_stream          = StreamDemux(data_stream(0), pe_select, config.pe_num) // vex to pe 1, 2, 3, 4

  val done                = Reg(Bool()) init False
  io.done := done
  when (io.srst) {
    done := False
  }

  io.itr_cnt := itr_cnt

  for (i <- 0 until config.pe_num) {
    io.vex2pe(i).payload.data  := vex_stream(i).payload.data
    io.vex2pe(i).valid         := vex_stream(i).valid
    vex_stream(i).ready        := True
  }

  vex_base_addr                 := Mux(ping_pong, io.vex_a_base, io.vex_b_base)
  vex_base_addr_r               := Mux(!ping_pong, io.vex_a_base, io.vex_b_base)

  // axi setup
  io.axiMemControlPort.aw.payload.prot 	:= B"3'b000"
  io.axiMemControlPort.aw.payload.size 	:= U"3'b110" //110 means that the 64 bytes in a transfer
  io.axiMemControlPort.aw.payload.addr  := vex_base_addr_r
  io.axiMemControlPort.aw.valid         := io.wb_valid

  io.axiMemControlPort.w.payload.data   := io.wb_payload
  io.axiMemControlPort.w.strb 			    := B"64'hffff_ffff_ffff_ffff"

  io.axiMemControlPort.ar.payload.prot 	:= B"3'b000"
  io.axiMemControlPort.ar.payload.size 	:= U"3'b110" // 64 bytes(512b) in a transfer
  io.axiMemControlPort.ar.payload.burst := B"2'b01"  // incr type
  io.axiMemControlPort.ar.len 			    := U"8'b0000_0000"
  io.axiMemControlPort.ar.payload.addr 	:= 0

  // Flow:
  //        data_stream  -> edge_stream | edge_stream | edge_stream | edge_stream -> pe_1 | pe_2 | pe_3 | pe_4

  for (i <- 0 until config.pe_num) {
    edge_stream(i).ready        := io.edge2pe(i).ready
    io.edge2pe(i).payload.data  := edge_stream(i).payload.data
    io.edge2pe(i).valid         := edge_stream(i).valid
  }

  // dispatcher 2 ge
  io.vex2ge.payload.data  := data_stream(0).payload.data
  io.vex2ge.valid         := False

  // edge read in, detect zero
  val single_zero_in       = UInt(4 bits)
  val partial_zero_in      = Bool()
  val partial_zero_in_r    = Reg(Bool()) init False
  val edge_in_ptr_in       = UInt(4 bits)

  for(i <- 0 until config.pe_num) {
    single_zero_in(i)  := (data_stream(1).payload.data.subdivideIn(128 bits)(i) === 0) && data_stream(1).valid
  }
  partial_zero_in := single_zero_in.orR

  when( partial_zero_in){
    partial_zero_in_r := True
  }

  switch(single_zero_in) {
    is (1) { edge_in_ptr_in := 0 }
    is (2) { edge_in_ptr_in := 1 }
    is (4) { edge_in_ptr_in := 2 }
    is (8) { edge_in_ptr_in := 3 }
    default { edge_in_ptr_in := 0 }
  }

  // edge send out, decide nxt CB, RB
  val header 		          = Bool()
  val head		            = UInt(4 bits)
  val header_ptr          = UInt(2 bits)
  val RB_nxt 		          = UInt(8 bits)
  val CB_nxt 		          = UInt(8 bits)
  val RB_nxt_r 		        = Reg(UInt(8 bits)) init 0
  val CB_nxt_r 		        = Reg(UInt(8 bits)) init 0
  val edge_length         = UInt(8 bits)
  val edge_length_r       = Reg(UInt(8 bits)) init 0
  val last_r              = Reg(Bool()) init False
  val RB_switch 		      = Reg(Bool()) init False

  for(i <- 0 until config.pe_num) {
    head(i) := (data_stream(1).payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(0) === 0x00 &&
                data_stream(1).payload.data.subdivideIn(128 bits)(i).subdivideIn(8 bits)(1) =/= 0x00)
  }

  // If multiple header is read in, the header with larger idx store the RB_nxt, CB_nxt, edge_length
  switch(head) {
    is (8, 9, 10, 11, 12, 13, 14, 15) { header_ptr := 3 }
    is (4, 5, 6, 7) { header_ptr := 2 }
    is (2, 3) { header_ptr := 1 }
    is (1) { header_ptr := 0 }
    default { header_ptr := 0 }
  }

  header := head.orR && data_stream(1).valid

  RB_nxt := header ? data_stream(1).payload.data.subdivideIn(128 bits)(header_ptr).subdivideIn(8 bits)(2).asUInt | 0
  CB_nxt := header ? data_stream(1).payload.data.subdivideIn(128 bits)(header_ptr).subdivideIn(8 bits)(3).asUInt | 0
  edge_length  := header ? data_stream(1).payload.data.subdivideIn(128 bits)(header_ptr).subdivideIn(8 bits)(4).asUInt | 0

  when (RB_nxt =/= 0) {
    RB_nxt_r := RB_nxt
  }

  when (CB_nxt =/= 0) {
    CB_nxt_r := CB_nxt
  }

  when (edge_length =/= 0) {
    edge_length_r := edge_length
  }

  // Dispatcher fsm
  pe_nxt_rdy := !io.pe_busy.andR

  io.RB_switch := RB_switch

  when(!io.pe_busy(0)) { pe_nxt := 0 }
    .elsewhen(!io.pe_busy(1)){ pe_nxt := 1}
    .elsewhen(!io.pe_busy(2)){ pe_nxt := 2}
    .elsewhen(!io.pe_busy(3)){ pe_nxt := 3}
    .otherwise{ pe_nxt := 0}

  val axi_addr_l   = UInt(32 bits)
  val axi_addr_h   = UInt(32 bits)
  val page_4k      = UInt(32 bits)
  val last_page    = UInt(8 bits)
  val next_page    = UInt(8 bits)
  val edge_addr    = Reg(UInt(16 bits)) init 0
  axi_addr_l := io.edge_base + (64) * edge_addr
  axi_addr_h := io.edge_base + (64) * (edge_addr + edge_length_r - 1)

  page_4k(31 downto 12) := axi_addr_h(31 downto 12)
  page_4k(11 downto 0)  := 0
  last_page := (( page_4k - axi_addr_l ) >> 6).resize(8)
  next_page := edge_length_r - last_page

  val axi4MemCtrlFsm = new StateMachine {
    val RB            = Reg(UInt(32 bits)) init 0
    val flg_4k        = Reg(Bool()) init True
    val axi_addr_l_r  = Reg(UInt(32 bits)) init 0
    val axi_addr_h_r  = Reg(UInt(32 bits)) init 0
    val fired         = Reg(Bool()) init False

    val IDLE = new State with EntryPoint {
      onEntry {
        disable_read ()
      }
      whenIsActive{
        when(io.start){
          goto(SEND_INIT_VEX_ADDR)
        }
      }
    }

    val SEND_INIT_VEX_ADDR: State = new State {
      onEntry {
        edge_length_r := io.qsb_cfg.CB_length.resize(8)
        edge_addr     := 0
      }
      whenIsActive {
        when(io.pe_busy.orR === False) {
          set_read(U(1), U(1), vex_base_addr + (128 * (io.qsb_cfg.CB_init.resize(8) - 1))) // read to initial vex addr
          enable_read()
        }
        when(io.axiMemControlPort.ar.fire) {
          disable_read()
          goto(READ_VEX_DATA_SEND_EDGE_ADDR)
        }
      }
    }

    val READ_VEX_DATA_SEND_EDGE_ADDR: State = new State {
      onEntry{
        vexEdgeSelect := 0
        get_nxt_pe()
        flg_4k := True
        axi_addr_l_r := axi_addr_l
        axi_addr_h_r := axi_addr_h
        fired := False
      }
      whenIsActive {
        // Deal with request cross AXI 4K boundary
        when (axi_addr_l_r >> 12 =/= axi_addr_h_r >> 12) {
          when (flg_4k) {
            enable_read()
            set_read(U(1), last_page - 1 , io.edge_base + (64) * edge_addr) // read first page
            when (io.axiMemControlPort.r.fire) {
              flg_4k    := False
            }
          } otherwise {
            enable_read()
            set_read(U(1), next_page - 1 , io.edge_base + (64) * (edge_addr + last_page)(15 downto 0)) // read next page
            when (io.axiMemControlPort.r.last) {
              disable_read()
              edge_addr := edge_addr + edge_length_r
              goto(READ_EDGE_DATA_SEND_VEX_ADDR)
            }
          }
        } otherwise {
          set_read(U(1), edge_length_r - 1, axi_addr_l) // read to edge addr
          enable_read()
          data_stream(0).ready := True
          when (io.axiMemControlPort.ar.fire) {
            fired := True
            edge_addr := edge_addr + edge_length_r
          }
          when (io.axiMemControlPort.ar.fire || fired) {
            disable_read()
          }
          when (io.axiMemControlPort.r.last) {
            disable_read()
            goto(READ_EDGE_DATA_SEND_VEX_ADDR)
          }
        } // normal request
      }
    }

    val READ_EDGE_DATA_SEND_VEX_ADDR: State = new State {
      onEntry{
        partial_zero_in_r := False
        vexEdgeSelect := 1
      }

      whenIsActive {
        when (io.axiMemControlPort.r.last) {
          last_r := io.axiMemControlPort.r.last
        }
        when(RB_nxt_r > 0) {
          RB_switch := True
        }
        when (io.axiMemControlPort.r.payload.last || last_r) {
          when (partial_zero_in  || partial_zero_in_r) {
            when(RB_nxt_r > 0 || RB_nxt > 0 || RB_switch) {
              when (io.pe_busy.orR === False) {
                set_read(U(2), U(15), vex_base_addr)
                enable_read()
              }
              when(io.axiMemControlPort.ar.fire) {
                partial_zero_in_r := False
                io.vex2ge.valid   := data_stream(0).valid
                goto(READ_VEX_DATA_GE_SEND_VEX_ADDR)
              }
            } otherwise {
              when (io.pe_busy.andR === False) {
                set_read(U(1), U(1), vex_base_addr + 128 * (CB_nxt_r - 1 ))
                enable_read()
              }
              when(io.axiMemControlPort.ar.fire) {
                partial_zero_in_r := False
                goto(READ_VEX_DATA_SEND_EDGE_ADDR)
              }
            }
          }
        }
      }
      onExit {
        last_r := False
      }
    }

    val READ_VEX_DATA_GE_SEND_VEX_ADDR :State = new State {
      onEntry{
        vexEdgeSelect   := 0
        io.axiMemControlPort.ar.valid   := False
      }
      whenIsActive{
        // After get all vertex, issue next vertex
        when(io.update_busy) {
          RB_switch     := False
        }
        when (io.axiMemControlPort.r.last) {
          last_r := io.axiMemControlPort.r.last
        }
        io.vex2ge.valid := data_stream(0).valid
        set_read(U(1), U(1), vex_base_addr + 128 * (CB_nxt_r - 1))
        // in last cycle,
        when(io.axiMemControlPort.r.last || last_r){
          when (itr_cnt === io.qsb_cfg.iteration) {
            done := True
            goto(IDLE)
          } otherwise {
            when(RB === io.qsb_cfg.RB_max) {
              ping_pong := !ping_pong
              itr_cnt   := itr_cnt + 1
              RB_nxt_r  := 0
              RB        := io.qsb_cfg.RB_init
              goto(SEND_INIT_VEX_ADDR)
            }.elsewhen (!io.update_busy) {
              enable_read()
              when (io.axiMemControlPort.ar.fire) {
                RB_nxt_r  := 0
                RB        := RB_nxt_r.resize(32)
                goto(READ_VEX_DATA_SEND_EDGE_ADDR)
              }
            }
          }
        }
      }
      onExit {
        last_r := False
      }
    }
  }

  def get_nxt_pe (): Unit = {
    pe_select := pe_nxt
  }

  def enable_read () = {
    io.axiMemControlPort.ar.valid := True
  }

  def disable_read () = {
    io.axiMemControlPort.ar.valid := False
  }

  def set_read (id: UInt, len: UInt, addr: UInt) = {
    io.axiMemControlPort.ar.payload.id    := id
    io.axiMemControlPort.ar.payload.len   := len
    io.axiMemControlPort.ar.payload.addr  := addr
  }

}//end of component
import spinal.core._

case class Pe_Core(

    vertex_reg_width: Int = 32,
    vertex_reg_addr_width: Int = 6,
    edge_width  : Int = 32,

    update_ram_addr_width: Int = 6,
    updata_ram_data_width: Int = 32,

    vertex_ram_addr_width: Int = 6,
    vertex_ram_data_width: Int = 32,

) extends Component {

    val io_edge_fifo = new Bundle {
        val edge_fifo_in        = in Bits(edge_width bits)
        val edge_fifo_noempty   = in Bool()
    }

    val io_vertex_reg = new Bundle {
        val vertex_reg_addr     = out Bits(vertex_reg_addr_width bits)
        val vertex_reg_in       = in SInt(vertex_width bits)
        val vertex_reg_full     = in Bool()
    }

    val io_update_ram = new Bundle {
        val update_ram_wr_valid  = out Bool()
        val update_ram_wr_addr   = out Bits(update_ram_addr_width bits)
        val update_ram_wr_data   = out Bits(32 bits)

        val update_ram_enb          = out Bool
        val read_addr_update_ram    = out Bits(update_ram_addr_width bits)
        val data_update_ram         = in Bits(32 bits)
    }

    val io_update_ram = new Bundle {
        val update_ram_wr_valid  = out Bool()
        val update_ram_wr_addr   = out Bits(update_ram_addr_width bits)
        val update_ram_wr_data   = out Bits(updata_ram_data_width bits)

        val update_ram_rd_valid  = out Bool
        val update_ram_rd_addr   = out Bits(update_ram_addr_width bits)
        val update_ram_rd_data   = in Bits(updata_ram_data_width bits)
    }

}

// state machine

    val pe_fsm = new StateMachine {

        val IDLE    = new State with EntryPoint
        val OPERATE = new State
        val FINISH  = new State

        IDLE
        .whenIsActive (
            when(edge_fifo_noempty && io.vertex_reg_done) {
                goto(OPERATE)
            }
        )

        OPERATE
        .whenIsActive {
            when(h3_valid && ~h2_valid) {
                goto(FINISH)
            }
        }

        FINISH
        .whenIsActive (
            when (switch_done) {
                goto(IDLE)
            }
        )
    }

// wire 
val updata_data_h2 = Bits(32 bits)

val update_ram_data_old_h2  = SInt(32 bits)
val vertex_reg_data_h2		= SInt(32 bits)
val ram_data_h3             = SInt(32 bits)

// Reg Group
val edge_value_h1 = Reg(Bits(8 bits)) init 0
val update_ram_addr_h1 = Reg(Bits(10 bits)) init 0
val h1_valid = Reg(Bool()) init 0


val h2_valid = Reg(Bool()) init 0
val new_updata_data_h2 = Reg(Bits(32 bits)) init 0


val h3_valid = Reg(Bool()) init 0
val ram_data_h3 = Reg(Bits(32 bits)) init 0


//-----------------------------------------------------------
// pipeline h0
//-----------------------------------------------------------



//-----------------------------------------------------------
// pipeline h1
//-----------------------------------------------------------

when (edge_fifo_ready) {
    vertex_reg_addr_h1  := edge(31 downto 22)
    update_ram_addr_h1  := edge(21 downto 12)
    edge_value_h1       := edge(11 downto 0)
    h1_valid            := True
} otherwise {
    vertex_addr         := 0
    update_ram_addr     := 0
    eage_value_h1       := 0
    h1_valid            := False
}

//-----------------------------------------------------------
// pipeline h2
//-----------------------------------------------------------

when (h1_valid) {
    edge_value_h2  		:= edge_value_h1
	update_ram_addr_h1  := update_ram_addr_h2
    h2_valid       		:= True
} otherwise {
	edge_value_h2  		:= 0
	update_ram_addr_h1  := 0
    h2_valid       		:= False
}

updata_data_h2 := update_ram_data_old_h2 + vertex_reg_data_h2 * ram_data_h3;

// hazard detect


//-----------------------------------------------------------
// pipeline h3
//-----------------------------------------------------------


when (h2_valid) {
	update_ram_addr_h3	:= update_ram_addr_h2
    ram_data_h3  		:= updata_data_h2
    h3_valid     		:= True
} otherwise {
	update_ram_addr_h3	:= 0
    ram_data_h3  		:= 0
    h3_valid     		:= False
}

}
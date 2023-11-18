import spinal.lib._

case class Gather_Pe_Core(

    alpha: SInt,
    beta: SInt,  
    xi_dt: SInt, 
    positive_boundary: SInt,
    negetive_boundary: SInt

    addr_width: Int = 6,
    data_width: Int = 32,

) extends Component {

    val io = new Bundle {
        val pe_done = in Bool()
        val gather_pe_done = out Bool()

        val rd_addr_update_ram = out Bits(addr_width bits)
        val rd_en_update_ram  = out Bool()
        val rd_data_update_ram = in Bits(data_width bits)
        
        val rd_addr_vertex_ram = out Bits(6 bits)
        val rd_en_vertex_ram  = out Bool()
        val rd_data_vertex_ram = out Bits(data_width bits)

        val wr_addr_vertex_ram = out Bits(6 bits)
        val wr_en_vertex_ram  = out Bool()
        val wr_data_vertex_ram = out Bits(16 bits)
    }

    val h1_valid = Reg(Bool()) init False
    val h2_valid = Reg(Bool()) init False
    val h3_valid = Reg(Bool()) init False
    val h4_valid = Reg(Bool()) init False

    val gather_pe_fsm = new StateMachine {

        val IDLE    = new State with EntryPoint
        val OPERATE = new State
        val FINISH  = new State

        IDLE
        .whenIsActive (
            when(io.pe_done) {
                goto(OPERATE)
            }
        )

        OPERATE
        .whenIsActive {
            when(h4_valid && ~h3_valid) {
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

//-----------------------------------------------------------
// pipeline h1: read updated value (J @ X_comp) and vertex ram (x_old)
//-----------------------------------------------------------

when (pe_done) {
    rd_addr_update_ram := 0
    rd_en_update_ram := False
    rd_addr_vertex_ram := 0
    rd_en_vertex_ram := False
} otherwise {
    rd_addr_update_ram := 0
    rd_en_update_ram := False
    rd_addr_vertex_ram := 0
    rd_en_vertex_ram := False
}

// -----------------------------------------------------------
// pipeline h2: y_new = y_old + ((-1 + alpha) *x_old + beta* updated value) * dt
// -----------------------------------------------------------

val y_new_h2 = Reg(SInt(7 bits)) init 0
val x_new_h3 = Reg(SInt(32 bits)) init 0
val alpha_h2 = Reg(SInt(32 bits)) init 0

when (h1_valid) {
    y_old_h2       := read_data_vertex_ram
    J_mul_x_old_h2 := read_data_update_ram
    x_old_h2       := read_data_vertex_ram
    h2_valid       := True
} otherwise {
    h2_valid       := False
}

y_new_h2 := y_old_h2 + ((-32 + alpha_h2) * x_old_h2 + beta * J_mul_x_old_h2) * xi_dt

// -----------------------------------------------------------
// pipeline h3: x_new = x_old + y_new * dt
//              y_comp[np.abs(x_comp) > 1] = 0  
//              np.clip(x_comp,-1, 1)
// -----------------------------------------------------------
when (h2_valid) {
    y_new_h3 := y_new_h2
    x_old_h3 := x_old_h2
    h3_valid := True
} otherwise {
    h3_valid := False
}

x_new_h3 := x_old_h3 + y_new_h2 * xi_dt

val x_new_cliped_h3 = SInt(8 bits)
val y_new_cliped_h3 = SInt(8 bits)

x_new_cliped_h3 = (x_new_h3 > positive_boundary) ? positive_boundary | (x_new < negetive_boundary) ? negetive_boundary | x_new(7 downto 0)
y_new_cliped_h3 = (x_new < positive_boundary ) && (x_new > negetive_boundary) ? y_new_delay1 | 0; 


// -----------------------------------------------------------
// pipeline h4: write back
// -----------------------------------------------------------

when (h3_valid) {
    wr_data_vertex_ram := x_new_cliped_h3 ## y_new_cliped_h3
    wr_addr_vertex_ram := wr_addr_vertex_ram + 1
    h4_valid := True
} otherwise {
    wr_data_vertex_ram := 0
    wr_addr_vertex_ram := 0
    h4_valid := False
}




}


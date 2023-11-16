import spinal.lib._
import PE._

case class pe_top(

    alpha: SInt = 1, //alpha = (np.linspace(0, 1, num_iters))
    beta: SInt = 8,  // beta = (0.7 / math.sqrt(N))#
    xi_dt: SInt = 32, 
    positive_boundary: SInt = 127,
    negetive_boundary: SInt = -1,    // np.clip(x_comp,-1, 1)

    axi_width: SInt = 128,
    fifo_depth: SInt = 64,

    update_ram_addr_width: Int = 64,
    vertex_ram_addr_width: Int = 64,
    update_ram_entry_num: Int = 64,
    vertex_ram_entry_num: Int = 64,
    update_ram_entry_width: Int = 32,
    vertex_ram_entry_width: Int = 32,

) extends Component {

    val io = new Bundle {
        val pe_idle         = out Bool()
        val fifo_stream     = slave Stream(Bits(128))
        val vertex_stream   = slave Stream(Bits(128))
    }

    val pe                  = pe_core(

    )
    val global_reg          = global_reg(

    )
    val fifo                =   fifo (  axi_width
                                        fifo_depth)

    val gather_pe           =   gather_pe_core( alpha,
                                                beta,
                                                xi_dt,
                                                positive_boundary,
                                                negetive_boundary )

    val switch_vertex_ram   =   Switch(vertex_ram_entry_width, 10)
    val switch_update_ram   =   Switch(update_ram_entry_width, 10)
    val update_ramA         =   Bram(update_ram_entry_num, 
                                    Bits(update_ram_entry_width))
    val update_ramB         =   Bram(update_ram_entry_num, Bits(update_ram_entry_type))
    val vertex_ramA         =   Bram(vertex_ram_entry_num, Bits(vertex_ram_entry_type))
    val vertex_ramB         =   Bram(vertex_ram_entry_num, Bits(vertex_ram_entry_type))




}


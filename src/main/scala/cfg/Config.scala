package cfg
import spinal.core._

object Config {

    var axi_width:          Int = 512
    var core_num:           Int = 4
    var ge_thread:          Int = 32

    var ram_depth:          Int = 16 * 32 / ge_thread
    var ram_addr_width:     Int = log2Up(ram_depth)

    val thread_num:         Int = 8
    val matrix_size:        Int = 64
    val x_comp_width:       Int = 8
    val data_width:         Int = 16
    val addr_width:         Int = log2Up(matrix_size)
    val edge_width :        Int = 4
    val extra_adder_num:    Int = 4
    val up_bound:           Int = 127
    val lo_bound:           Int = -127     // np.clip(x_comp,-1, 1)
    val xy_width:           Int = 8
    val haz_table_width:    Int = 4
    val spmv_w:             Int = 24
    val pe_width:           Int = 128
    val addrWid:            Int = 32
    val idWid:              Int = 4
    val pe_thread:          Int = 8
}




                           



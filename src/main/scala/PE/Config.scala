package PE
import spinal.core._

object PEConfig {
    val axi_width:          Int = 128
    val axi_extend_width:   Int = 512

    val core_num:           Int = 4
    val thread_num:         Int = 8
    val matrix_size:        Int = 64
    val x_comp_width:       Int = 8
    val data_width:         Int = 16
    val addr_width:         Int = log2Up(matrix_size)
    val extend_addr_width:  Int = log2Up(matrix_size * thread_num)
    val tag_width:          Int = 4
    val edge_width :        Int = 4
    val extra_adder_num:    Int = 4
    val fifo_depth_1024:    Int = 1024
    val alpha:              Int = 1      //alpha = (np.linspace(0, 1, num_iters))
    val beta:               Int = 8      // beta = (0.7 / math.sqrt(N))#
    val xi_dt:              Int = 32
    val positive_boundary:  Int = 127
    val negetive_boundary:  Int = -1     // np.clip(x_comp,-1, 1)
    val quant_precision_8:  Int = 8
    val xy_width:           Int = 8
    val haz_table_width:    Int = 4
    val spmv_w:             Int = 24
}





                           



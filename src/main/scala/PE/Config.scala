package PE
import spinal.core._

object Config {
    val axi_width:          Int = 128
    val axi_extend_width:   Int = 512

    val core_num:           Int = 4
    val thread_num:         Int = 8
    val matrix_size:        Int = 64

    val data_width:         Int = 16
    val addr_width:         Int = log2Up(matrix_size)
    val extend_addr_width:  Int = log2Up(matrix_size * thread_num)
    val tag_width:          Int = 4
    val tag_width_full:     Int = tag_width * thread_num
    val tag_extend_width:   Int = tag_width_full * 4
    val edge_width :        Int = 4
    val vertex_read_cnt_max:Int = (matrix_size * data_width) / axi_extend_width
    val vertex_read_pointer_size:Int = 7
    val vertex_write_slice: Int = axi_extend_width / data_width
    val extra_adder_num:    Int = 4

    val fifo_depth_32:      Int = 32
    val fifo_depth_128:     Int = 128
    val fifo_depth_1024:    Int = 1024

    val alpha:              Int = 1      //alpha = (np.linspace(0, 1, num_iters))
    val beta:               Int = 8      // beta = (0.7 / math.sqrt(N))#
    val xi_dt:              Int = 32
    val positive_boundary:  Int = 127
    val negetive_boundary:  Int = -1     // np.clip(x_comp,-1, 1)

    val quant_precision_8:  Int = 8
    val quant_precision_32: Int = 32

    val xy_width:           Int = 8
    val haz_table_width:    Int = 4

    val spmm_prec:          Int = 31
}

case class PeConfig(axi_width:          Int = Config.axi_width,
                    axi_extend_width:   Int = Config.axi_extend_width,
                    core_num:           Int = Config.core_num,
                    thread_num:         Int = Config.thread_num,
                    matrix_size:        Int = Config.matrix_size,

                    data_width:         Int = Config.data_width,
                    addr_width:         Int = Config.addr_width,
                    extend_addr_width:  Int = Config.extend_addr_width,
                    tag_width:          Int = Config.tag_width,
                    tag_width_full:     Int = Config.tag_width_full,
                    tag_extend_width:   Int = Config.tag_extend_width,
                    edge_width:         Int = Config.edge_width,
                    vertex_read_cnt_max:Int = Config.vertex_read_cnt_max,
                    vertex_read_pointer_size:Int = Config.vertex_read_pointer_size,
                    vertex_write_slice: Int = Config.vertex_write_slice,
                    extra_adder_num:    Int = Config.extra_adder_num,

                    fifo_depth_32:      Int = Config.fifo_depth_32,
                    fifo_depth_128:     Int = Config.fifo_depth_128,
                    fifo_depth_1024:    Int = Config.fifo_depth_1024,

                    alpha:              Int = Config.alpha,
                    beta:               Int = Config.beta,
                    xi_dt:              Int = Config.xi_dt,
                    positive_boundary:  Int = Config.positive_boundary,
                    negetive_boundary:  Int = Config.negetive_boundary,

                    quant_precision_8:  Int = Config.quant_precision_8,
                    quant_precision_32: Int = Config.quant_precision_32,
                    xy_width:           Int = Config.xy_width,
                    haz_table_width:    Int = Config.haz_table_width,
                    spmm_prec:          Int = Config.spmm_prec)





                           



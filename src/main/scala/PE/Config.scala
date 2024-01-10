package PE

import spinal.core._

object Config {
    val axi_width: Int = 128
    val axi_extend_width: Int = 128
    val data_width: Int = 16
    val matrix_size: Int = 64
    val fifo_depth: Int = 128
    val addr_width: Int = log2Up(matrix_size)
    def test(): Unit = {
        println(addr_width)
    }
    def main(args: Array[String]): Unit = {
        test()
    }  
}
case class RegConfig(reg_depth: Int = Config.matrix_size,
                     addr_width: Int = Config.addr_width,
                     data_width: Int = Config.data_width)

case class ConvertConfig(axi_extend_width: Int = 512,
                         axi_width: Int = 128,
                         thread_num: Int = 8,
                         core_num: Int = 4,
                         data_width: Int = 16,
                         fifo_depth: Int = 1000)

case class FifoConfig(fifo_depth: Int = Config.fifo_depth,
                        stream_width: Int = Config.axi_width,
                        data_width: Int = Config.data_width)
case class VertexConfig(stream_width: Int = Config.axi_width,
                        reg_depth: Int = Config.matrix_size,
                        addr_width: Int = Config.addr_width,
                        data_width: Int = Config.data_width)
case class GlobalRegConfig(stream_width: Int = Config.axi_extend_width,
                           reg_depth: Int = Config.matrix_size,
                           addr_width: Int = Config.addr_width,
                           data_width: Int = Config.data_width)
case class PeCoreConfig(vertex_data_width: Int = 16,
                        vertex_addr_width: Int = 6,
                        edge_data_width: Int = 16,
                        edge_addr_width: Int = 6,
                        update_addr_width : Int = 6,
                        update_data_width : Int = 16,
                        edge_value_length : Int = 4)

case class GatherPeCoreConfig(alpha: Int = 1, //alpha = (np.linspace(0, 1, num_iters))
                              beta: Int = 8, // beta = (0.7 / math.sqrt(N))#
                              xi_dt: Int = 32,
                              positive_boundary: Int  = 127,
                              negetive_boundary: Int  = -1, // np.clip(x_comp,-1, 1)
                              addr_width: Int = 6,
                              data_width: Int = 16)
case class PETopConfig(core_num: Int = 4,
                       thread_num: Int = 8,
                       vertex_reg_num: Int = 8,
                       gather_pe_num: Int = 8,
                       data_width: Int = Config.data_width,
                       matrix_size: Int = 64,
                       vertex_config: VertexConfig  = VertexConfig(),
                       reg_config: RegConfig  = RegConfig(),
                       gather_pe_bundle_config: GatherPeCoreConfig = GatherPeCoreConfig(),
                       pe_bundle_config: PeBundleConfig = PeBundleConfig(),
                       pe_fifo_config: FifoConfig =FifoConfig(),
                       high_to_low_converter_config: ConvertConfig =ConvertConfig())

case class PeBundleConfig(axi_width: Int = 128,
                          addr_width: Int = 6,
                          vertex_width: Int = 16,
                          update_ram_addr_width: Int = 6,
                          vertex_ram_addr_width: Int = 6,
                          update_ram_depth: Int = 64,
                          vertex_ram_depth: Int = 64,
                          update_ram_width: Int = 16,
                          vertex_ram_width: Int = 16,
                          global_reg_config: GlobalRegConfig = GlobalRegConfig(),
                          reg_config: RegConfig = RegConfig(),
                          pe_core_config: PeCoreConfig = PeCoreConfig())




                           



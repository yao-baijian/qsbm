package PE



object Config {
    val data_width: Int = 16
//    def get(): Unit = {
//        return this.
//    }
}
case class PETopConfig(core_num: Int = 4,
                       thread_num: Int = 8,
                       vertex_reg_num: Int = 8,
                       gather_pe_num: Int = 8,
                       data_width: Int = Config.data_width,
                       matrix_size: Int = 64,
                       vertex_config: VertexConfig  = VertexConfig(),
                       reg_config: RegConfig  = RegConfig(),
                       gather_pe_bundle_config: GatherPeCoreConfig = GatherPeCoreConfig(),
                       pe_bundle_config: PeBundleConfig = PeBundleConfig())

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

case class GatherPeConfig(reg_depth: Int = 64,
                     addr_width: Int = 6,
                     data_width: Int = 16)

case class RegConfig(reg_depth: Int = 64,
                     addr_width: Int = 6,
                     data_width: Int = 16)

case class VertexConfig(reg_depth: Int = 64,
                     addr_width: Int = 6,
                     data_width: Int = 16,
                        stream_width: Int = 128)

case class GlobalRegConfig(stream_width: Int = 128,
                           reg_depth: Int = 64,
                           addr_width: Int = 6,
                           data_width: Int = 16)
                           
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


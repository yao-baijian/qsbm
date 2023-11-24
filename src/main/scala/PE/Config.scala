package PE

import spinal.core.SInt

case class PeBundleConfig(alpha: SInt = 1, //alpha = (np.linspace(0, 1, num_iters))
                          beta: SInt = 8, // beta = (0.7 / math.sqrt(N))#
                          xi_dt: SInt = 32,
                          positive_boundary: SInt = 127,
                          negetive_boundary: SInt = -1, // np.clip(x_comp,-1, 1)
                          axi_width: Int = 128,

                          addr_width: Int = 6,
                          vertex_width: Int = 16,
                          update_ram_addr_width: Int = 64,
                          vertex_ram_addr_width: Int = 64,
                          update_ram_depth: Int = 64,
                          vertex_ram_depth: Int = 64,
                          update_ram_width: Int = 32,
                          vertex_ram_width: Int = 32,
                          global_reg_config: GlobalRegConfig = GlobalRegConfig(),
                          reg_config: RegConfig = RegConfig(),
                          pe_core_config: PeCoreConfig = PeCoreConfig())
case class GatherPeConfig(reg_depth: Int = 64,
                     addr_width: Int = 6,
                     data_width: Int = 16)
case class RegConfig(reg_depth: Int = 64,
                     addr_width: Int = 6,
                     data_width: Int = 16)

case class GlobalRegConfig(stream_width: Int = 128,
                           reg_depth: Int = 64,
                           addr_width: Int = 6,
                           data_width: Int = 16)
case class PeCoreConfig(vertex_reg_data_width: Int = 16,
                        vertex_reg_addr_width: Int = 6,
                        edge_width: Int = 16,

                        update_ram_addr_width: Int = 6,
                        updata_ram_data_width: Int = 16,

                        vertex_ram_addr_width: Int = 6,
                        vertex_ram_data_width: Int = 16)

case class GatherPeCoreConfig(alpha: SInt,
                              beta: SInt,
                              xi_dt: SInt,
                              positive_boundary: SInt,
                              negetive_boundary: SInt,

                              addr_width: Int = 6,
                              data_width: Int = 32)

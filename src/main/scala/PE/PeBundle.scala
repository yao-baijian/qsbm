package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps


case class PeBundle(config: PeBundleConfig) extends Component {

    val io_state = new Bundle {
        val switch_done = in Bool()
        val last_update = in Bool()
        val bundle_busy = out Bool()
    }

    val io_fifo       =  new Bundle {
        val pe_fifo = Vec(slave Stream (Bits(config.axi_width / 8 bits)), 8)
    }

    val io_global_reg =new Bundle {
        val vertex_stream = slave Stream (Bits(config.axi_width / 8 bits))
    }

    val io_update_reg = new Bundle {
        val update_reg_wr_valid = Vec(out Bool(), 8)
        val update_reg_wr_addr = Vec(out UInt (config.update_ram_addr_width bits), 8)
        val update_reg_wr_data = Vec(out Bits (config.vertex_width bits), 8)

        val update_reg_rd_addr = Vec(out UInt (config.update_ram_addr_width bits), 8)
        val update_reg_rd_data = Vec(in Bits (config.vertex_width bits))
    }

    val last_update_r       = Reg(Bool())
    val need_new_vertex_r   = Reg(Bool())
    val pe_bundle           = new Array[PeCore](8)
    val global_reg          = GlobalReg (config.global_reg_config)

    global_reg.io.in_stream <> io_global_reg.vertex_stream
    global_reg.io.need_new_vertex <> need_new_vertex_r

    for (i <- 0 until 7) {
        pe_bundle (i) = PeCore(config.pe_core_config)
        pe_bundle (i).setName("pe_" + i.toString)

        pe_bundle(i).io_state.last_update <> last_update_r
        pe_bundle(i).io_state.switch_done <> io_state.switch_done
        pe_bundle(i).io_state.globalreg_done <> global_reg.io.reg_full

        pe_bundle(i).io_vertex_reg.vertex_reg_addr <> global_reg.io.rd_addr(i)
        pe_bundle(i).io_vertex_reg.vertex_reg_in <> global_reg.io.rd_data(i)

        pe_bundle(i).io_update_ram.update_ram_wr_valid <>  io_update_reg.update_reg_wr_valid(i)
        pe_bundle(i).io_update_ram.update_ram_wr_addr <>  io_update_reg.update_reg_wr_addr(i)
        pe_bundle(i).io_update_ram.update_ram_wr_data <>  io_update_reg.update_reg_wr_data(i)

        pe_bundle(i).io_update_ram.update_ram_rd_addr <>  io_update_reg.update_reg_rd_addr(i)
        pe_bundle(i).io_update_ram.update_ram_rd_data <>  io_update_reg.update_reg_rd_data(i)

        pe_bundle(i).io_edge_fifo.edge_fifo_ready <> io_fifo.pe_fifo(i).ready
        pe_bundle(i).io_edge_fifo.edge_fifo_valid <> io_fifo.pe_fifo(i).valid
        pe_bundle(i).io_edge_fifo.edge_fifo_in <> io_fifo.pe_fifo(i).payload

    }

    io_state.bundle_busy := pe_bundle(1).io_state.pe_busy | pe_bundle(2).io_state.pe_busy | pe_bundle(3).io_state.pe_busy | pe_bundle(4).io_state.pe_busy |
      pe_bundle(5).io_state.pe_busy | pe_bundle(6).io_state.pe_busy | pe_bundle(7).io_state.pe_busy | pe_bundle(0).io_state.pe_busy

    when (io_state.last_update) {
        last_update_r := True
    } elsewhen (io_state.switch_done) {
        last_update_r := False
    }

    when(pe_bundle(1).io_state.need_new_vertex & pe_bundle(2).io_state.need_new_vertex & pe_bundle(3).io_state.need_new_vertex & pe_bundle(4).io_state.need_new_vertex |
      pe_bundle(5).io_state.need_new_vertex & pe_bundle(6).io_state.need_new_vertex & pe_bundle(7).io_state.need_new_vertex & pe_bundle(0).io_state.need_new_vertex) {
        need_new_vertex_r := True
    } otherwise {
        need_new_vertex_r := False
    }
}


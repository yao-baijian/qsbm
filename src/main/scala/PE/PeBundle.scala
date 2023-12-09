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
        val need_new_vertex = out Vec(Bool(), 8)
        val pe_fifo = Vec(slave Stream (Bits(config.axi_width / 8 bits)), 8)
    }

    val io_global_reg =new Bundle {
        val vertex_stream = slave Stream (Bits(config.axi_width bits))
    }

    val io_update_reg = new Bundle {
        val wr_valid = Vec(out Bool(), 8)
        val wr_addr = Vec(out UInt (config.update_ram_addr_width bits), 8)
        val wr_data = Vec(out Bits (config.vertex_width bits), 8)

        val rd_addr = Vec(out UInt (config.update_ram_addr_width bits), 8)
        val rd_data = Vec(in Bits (config.vertex_width bits), 8)
    }

    val last_update_r       = Reg(Bool()) init False
    val need_new_vertex_r   = Reg(Bool()) init False
    val pe_bundle           = new Array[PeCore](8)
    val global_reg          = GlobalReg (config.global_reg_config)

    global_reg.io.in_stream << io_global_reg.vertex_stream
    global_reg.io.need_new_vertex <> need_new_vertex_r

    for (i <- 0 until 8) {
        pe_bundle (i) = PeCore(config.pe_core_config)
        pe_bundle (i).setName("pe_" + i.toString)

        pe_bundle(i).io_state.last_update <> last_update_r
        pe_bundle(i).io_state.switch_done <> io_state.switch_done
        pe_bundle(i).io_state.globalreg_done <> global_reg.io.reg_full

        pe_bundle(i).io_vertex_reg.addr <> global_reg.io.rd_addr(i)
        pe_bundle(i).io_vertex_reg.data <> global_reg.io.rd_data(i)

        pe_bundle(i).io_update_ram.wr_valid <>  io_update_reg.wr_valid(i)
        pe_bundle(i).io_update_ram.wr_addr <>  io_update_reg.wr_addr(i)
        pe_bundle(i).io_update_ram.wr_data <>  io_update_reg.wr_data(i)

        pe_bundle(i).io_update_ram.rd_addr <>  io_update_reg.rd_addr(i)
        pe_bundle(i).io_update_ram.rd_data <>  io_update_reg.rd_data(i)

        io_fifo.pe_fifo(i).ready := pe_bundle(i).io_edge_fifo.edge_fifo_ready
        pe_bundle(i).io_edge_fifo.edge_fifo_valid := io_fifo.pe_fifo(i).valid
        pe_bundle(i).io_edge_fifo.edge_fifo_in := io_fifo.pe_fifo(i).payload
        
        io_fifo.need_new_vertex(i) := pe_bundle(i).io_state.need_new_vertex

    }

    val bundle_busy_table = Bits(8 bits)
    bundle_busy_table := 0
    val bundle_need_vertex_table = Bits(8 bits)
    bundle_need_vertex_table := 0

    for (i <- 0 until 8) {
        bundle_busy_table(i) := pe_bundle(i).io_state.pe_busy
        bundle_need_vertex_table(i) := pe_bundle(i).io_state.need_new_vertex
    }

    io_state.bundle_busy := bundle_busy_table.orR

    when (io_state.last_update) {
        last_update_r := True
    } elsewhen (io_state.switch_done) {
        last_update_r := False
    }

    when(bundle_need_vertex_table.andR) {
        need_new_vertex_r := True
    } otherwise {
        need_new_vertex_r := False
    }
}


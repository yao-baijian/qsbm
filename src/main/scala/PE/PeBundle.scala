package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps


case class PeBundle(config: PeConfig) extends Component {

    val io_state = new Bundle {
        val switch_done = in Bool()
        val last_update = in Bool()
        val all_zero    = in Bool()
        val bundle_busy = out Bool()
    }
  
    val io_fifo       =  new Bundle {
        val globalreg_done  = out Bool()
        val pe_fifo         = Vec(slave Stream (Bits(config.data_width bits)), config.thread_num)
        val pe_tag          = Vec(slave Stream (Bits(config.tag_width bits)), config.thread_num)
    }

    val io_global_reg =new Bundle {
        val vertex_stream   = slave Stream (Bits(config.axi_extend_width bits))
        val reg_full        = out Bool()
    }

    val io_update_reg = new Bundle {
        val wr_valid = Vec(out Bool(), config.thread_num)
        val wr_addr = Vec(out UInt (config.extend_addr_width bits), config.thread_num)
        val wr_data = Vec(out Bits (config.data_width bits), config.thread_num)
        val rd_addr = Vec(out UInt (config.extend_addr_width bits), config.thread_num)
        val rd_data = Vec(in Bits (config.data_width bits), config.thread_num)
    }

    val need_vertex         = Bool()
    val pe_config           = PeConfig()
    val last_update_r       = Reg(Bool()) init False
    val need_new_vertex_r   = Reg(Bool()) init False
    val pe_core             = PeCore(pe_config)
    val global_reg          = GlobalReg (pe_config)

    global_reg.io.in_stream         <> io_global_reg.vertex_stream
    global_reg.io.srst              <> need_new_vertex_r
    io_fifo.globalreg_done          <> global_reg.io.reg_full
    io_global_reg.reg_full          <> global_reg.io.reg_full

    pe_core.io_state.last_update    <> last_update_r
    pe_core.io_state.switch_done    <> io_state.switch_done
    pe_core.io_state.globalreg_done <> global_reg.io.reg_full
    pe_core.io_state.all_zero       <> io_state.all_zero
    io_state.bundle_busy            <> pe_core.io_state.pe_busy
    need_vertex                     <> pe_core.io_state.need_new_vertex

    for (i <- 0 until config.thread_num) {
        pe_core.io_vertex.addr(i)       <> global_reg.io.rd_addr(i)
        pe_core.io_vertex.data(i)       <> global_reg.io.rd_data(i)

        pe_core.io_update.wr_valid(i)   <>  io_update_reg.wr_valid(i)
        pe_core.io_update.wr_addr(i)    <>  io_update_reg.wr_addr(i)
        pe_core.io_update.wr_data(i)    <>  io_update_reg.wr_data(i)
        pe_core.io_update.rd_addr(i)    <>  io_update_reg.rd_addr(i)
        pe_core.io_update.rd_data(i)    <>  io_update_reg.rd_data(i)

        io_fifo.pe_fifo(i).ready        <> pe_core.io_edge.edge_ready(i)
        io_fifo.pe_tag(i).ready         <> pe_core.io_edge.edge_ready(i)
        pe_core.io_edge.edge_valid(i)   <> io_fifo.pe_fifo(i).valid
        pe_core.io_edge.edge_value(i)   <> io_fifo.pe_fifo(i).payload
        pe_core.io_edge.tag_value(i)    <> io_fifo.pe_tag(i).payload
    }

    when (io_state.last_update) {
        last_update_r := True
    } elsewhen (io_state.switch_done) {
        last_update_r := False
    }

    when(need_vertex) {
        need_new_vertex_r := True
    } otherwise {
        need_new_vertex_r := False
    }
}


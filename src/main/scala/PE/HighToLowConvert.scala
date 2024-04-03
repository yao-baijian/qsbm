package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class HighToLowConvert(config:PeConfig) extends Component {

    val io = new Bundle {
        val in_edge_stream = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val in_tag_stream = Vec(slave Stream (Bits(config.tag_extend_width bits)), config.core_num)
        val out_edge_stream = Vec(Vec(master Stream (Bits(config.data_width bits)), config.thread_num), config.core_num)
        val out_tag_stream = Vec(Vec(master Stream (Bits(config.tag_width - 1 bits)), config.thread_num), config.core_num)
    }
    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------
    val counter_group       = new Array[Counter](config.core_num)
    val edge_convert_fifo   = new Array[StreamFifo[Bits]](config.core_num)
    val tag_convert_fifo    = new Array[StreamFifo[Bits]](config.core_num)
    val all_zero_ff         = Vec(Reg(Bool()) init True ,config.core_num)
    val single_zero_inval   = Vec(Vec(Bool(),config.thread_num),config.core_num)
    val ready_table         = Vec(Vec(Bool(),config.thread_num),config.core_num)
    val ready_reg           = Vec(Reg(Bool()) init True ,config.core_num)
    //-----------------------------------------------------
    // Module Instantiation
    //-----------------------------------------------------
    for (i <- 0 until config.core_num) {
        edge_convert_fifo(i)= StreamFifo(Bits(config.axi_extend_width bits), config.fifo_depth_1024)
        tag_convert_fifo(i) = StreamFifo(Bits(config.tag_extend_width bits), config.fifo_depth_1024)
        counter_group(i)    = Counter(0 until config.core_num )
        counter_group(i).setName("Counter_" + i.toString)
    }
    //-----------------------------------------------------
    // Wiring
    //-----------------------------------------------------
    for (i <- 0 until config.core_num) {
        edge_convert_fifo(i).io.push.valid      := io.in_edge_stream(i).valid
        tag_convert_fifo(i).io.push.valid       := io.in_edge_stream(i).valid
        edge_convert_fifo(i).io.push.payload    := io.in_edge_stream(i).payload
        tag_convert_fifo(i).io.push.payload     := io.in_tag_stream(i).payload
        io.in_edge_stream(i).ready              := edge_convert_fifo(i).io.push.ready
        io.in_tag_stream(i).ready               := edge_convert_fifo(i).io.push.ready

        for (j <- 0 until config.thread_num) {
            io.out_edge_stream(i)(j).valid  := all_zero_ff(i) & single_zero_inval(i)(j) & edge_convert_fifo(i).io.pop.valid
            io.out_tag_stream(i)(j).valid   := io.out_edge_stream(i)(j).valid
            io.out_edge_stream(i)(j).payload:= edge_convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i).value)(config.data_width * (j + 1) - 1 downto config.data_width * j)
            io.out_tag_stream(i)(j).payload := tag_convert_fifo(i).io.pop.payload.subdivideIn(config.tag_width_full bits)(counter_group(i).value)(config.tag_width * (j + 1) - 2 downto config.tag_width * j)
            ready_table(i)(j)               := io.out_edge_stream(i)(j).ready
        }
        when (counter_group(i).value === 0)  {
            ready_reg(i) := ready_table(i).andR
        }
        edge_convert_fifo(i).io.pop.ready   := ready_reg(i) && (counter_group(i).value === 3)
        tag_convert_fifo(i).io.pop.ready    := ready_reg(i) && (counter_group(i).value === 3)
        when(!edge_convert_fifo(i).io.pop.valid) {
            counter_group(i).clear()
        } otherwise {
            counter_group(i).increment()
        }

        val all_zero_inval         = Vec(Bool(),config.core_num)
        when(edge_convert_fifo(i).io.pop.valid) {
            when(!all_zero_inval(i)) {
                all_zero_ff(i) := False
            }
        } otherwise {
            all_zero_ff(i) := True
        }

        when(edge_convert_fifo(i).io.pop.valid && edge_convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i).value) === 0) {
            all_zero_inval(i) := False
        } otherwise {
            all_zero_inval(i) := True
        }

        for (j <- 0 until config.thread_num) {
            when(edge_convert_fifo(i).io.pop.valid && all_zero_inval(i) && edge_convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i).value)(config.data_width * (j + 1) - 1 downto config.data_width * j) === 0) {
                single_zero_inval(i)(j) := False
            } otherwise {
                single_zero_inval(i)(j) := True
            }
        }
    }
}


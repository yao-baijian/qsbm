package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class HighToLowConvert(config:ConvertConfig) extends Component {

    val io = new Bundle {
        val in_edge_stream = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val out_edge_stream = Vec(Vec(master Stream (Bits(config.data_width bits)), config.thread_num), config.core_num)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val counter_group       = new Array[Counter](4)
    val convert_fifo        = new Array[StreamFifo[Bits]](4)
    val all_zero_inval      = Vec(Bool(),4)
    val single_zero_inval   = Vec(Vec(Bool(),config.thread_num),config.core_num)
    val ready_table         = Vec(Vec(Bool(),config.thread_num),config.core_num)


    //-----------------------------------------------------
    // Module Instantiation
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        convert_fifo(i) = StreamFifo(Bits(config.axi_extend_width bits), config.fifo_depth)
        counter_group(i) = Counter(0 to 3)
    }

    //-----------------------------------------------------
    // Wiring
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {

        convert_fifo(i).io.push.valid := io.in_edge_stream(i).valid
        convert_fifo(i).io.push.payload := io.in_edge_stream(i).payload(config.axi_extend_width downto 0)
        io.in_edge_stream(i).ready := convert_fifo(i).io.push.ready

        // Todo Add Valid Index (all zero logic)
        for (j <- 0 until config.thread_num) {
            io.out_edge_stream(i)(j).valid := convert_fifo(i).io.pop.valid & all_zero_inval(i) & single_zero_inval(i)(j)
            io.out_edge_stream(i)(j).payload := convert_fifo(i).io.pop.payload.subdivideIn(128 bits)(counter_group(i))(16 * (j + 1) - 1 downto 16 * j)
            ready_table(i)(j) := io.out_edge_stream(i)(j).ready
        }
        convert_fifo(i).io.pop.ready := ready_table(i).andR && (counter_group(i) === 3)

        when(!convert_fifo(i).io.pop.valid) {
            counter_group(i).clear()
        } otherwise {
            counter_group(i).increment()
        }

        when(!convert_fifo(i).io.pop.valid) {
            all_zero_inval(i) := True
        } elsewhen (convert_fifo(i).io.pop.payload.subdivideIn(128 bits)(counter_group(i)) === 0) {
            all_zero_inval(i) := False
        }

        for (j <- 0 until config.thread_num) {
            when(!(convert_fifo(i).io.pop.valid & all_zero_inval(i))) {
                when(convert_fifo(i).io.pop.payload.subdivideIn(128 bits)(counter_group(i))(16 * (j + 1) - 1 downto 16 * j) === 0) {
                    single_zero_inval(i)(j) := False
                } otherwise {
                    single_zero_inval(i)(j) := True
                }
            } otherwise {
                single_zero_inval(i)(j) := True
            }
        }
    }
}


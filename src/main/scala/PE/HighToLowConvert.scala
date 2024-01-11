package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class HighToLowConvert(config:PeConfig) extends Component {

    val io = new Bundle {
        val in_edge_stream = Vec(slave Stream (Bits(config.axi_extend_width bits)), config.core_num)
        val out_edge_stream = Vec(Vec(master Stream (Bits(config.data_width bits)), config.thread_num), config.core_num)
    }

    //-----------------------------------------------------
    // Module Declaration
    //-----------------------------------------------------

    val counter_group       = new Array[Counter](config.core_num)
    val convert_fifo        = new Array[StreamFifo[Bits]](config.core_num)
    val all_zero_inval      = Vec(Bool() ,config.core_num)
    val single_zero_inval   = Vec(Vec(Bool(),config.thread_num),config.core_num)
    val ready_table         = Vec(Vec(Bool(),config.thread_num),config.core_num)


    //-----------------------------------------------------
    // Module Instantiation
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        convert_fifo(i) = StreamFifo(Bits(config.axi_extend_width bits), config.fifo_depth_1024)
        counter_group(i) = Counter(0 until config.core_num )
    }

    //-----------------------------------------------------
    // Wiring
    //-----------------------------------------------------

    for (i <- 0 until config.core_num) {
        convert_fifo(i).io.push.valid := io.in_edge_stream(i).valid
        convert_fifo(i).io.push.payload := io.in_edge_stream(i).payload(config.axi_extend_width - 1 downto 0)
        io.in_edge_stream(i).ready := convert_fifo(i).io.push.ready

        // Todo Add Valid Index (all zero logic)
        for (j <- 0 until config.thread_num) {
            io.out_edge_stream(i)(j).valid := convert_fifo(i).io.pop.valid & all_zero_inval(i) & single_zero_inval(i)(j)
            io.out_edge_stream(i)(j).payload := convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i))(config.data_width * (j + 1) - 1 downto config.data_width * j)
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
        } otherwise {
            when (convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i)) === 0) {
                all_zero_inval(i) := False
            } otherwise {
                all_zero_inval(i) := True
            }

        }

        for (j <- 0 until config.thread_num) {
            when(!(convert_fifo(i).io.pop.valid)) {
                when(!(convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i)) === 0)) {
                    when (convert_fifo(i).io.pop.payload.subdivideIn(config.axi_width bits)(counter_group(i))(config.data_width * (j + 1) - 1 downto config.data_width * j) === 0) {
                        single_zero_inval(i)(j) := False
                    } otherwise {
                        single_zero_inval(i)(j) := True
                    }
                } otherwise {
                    single_zero_inval(i)(j) := True
                }
            } otherwise {
                single_zero_inval(i)(j) := True
            }
        }
    }
}


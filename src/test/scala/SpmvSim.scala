import dispatcher._
import spinal.core.sim._
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver
import test.SboomTop

import scala.collection.mutable.{Seq, _}
import scala.math.sqrt
import scala.sys.process._

class SpmvSim extends TestBase {

  object MySpinalConfig extends SpinalConfig(

    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = ASYNC,
      resetActiveLevel = HIGH
    ),
    defaultClockDomainFrequency = FixedFrequency(300 MHz),
    targetDirectory = "fpga/target",
    oneFilePerComponent = false
  )

  val simConfig     = SpinalSimConfig(_spinalConfig = MySpinalConfig)
  val simulator     = "Verilator"
  val compiled      = simulator match {
    case "Verilator" =>
      simConfig
        .withWave
        .compile(SboomTop(Config()))
    case "Iverilog" =>
      simConfig
        .withWave
        .withIVerilog
        .compile(SboomTop(Config()))
    case "Xsim" =>
      simConfig
        .withWave
        .withXSim
        .withXilinxDevice("xcu280-fsvh2892-2L-e")
        .compile(SboomTop(Config()))
    case _ =>
      throw new IllegalArgumentException("Unsupported simulator")
  }

  test("SpmvSim") {
    compiled.doSim { dut =>
      dut.clockDomain.forkStimulus(100)

      // algorithm
      val result = Seq("python", "quantization/spinal_test.py",
        filename,
        bestknown.toString,
        cmp_type,
        num_iter.toString,
        dbg_iter.toString,
        typ,
        "ON").!!

      val lines         = result.split("\n")
      val x_comp_init   = lines(0).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val y_comp_init   = lines(1).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val JX_dbg        = lines(2).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toInt)
      val x_comp_dbg    = lines(3).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val y_comp_dbg    = lines(4).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toInt)
      val partial_sum_dbg1 = lines(5).split(",").map(_.toFloat)
      val partial_sum_dbg2 = lines(6).split(",").map(_.toFloat)
      //      val qsb_energy    = lines(7).split(",").map(_.toFloat)

      val combined_init = new Array[Byte] (x_comp_init.length * 2)

      for (i <- x_comp_init.indices) {
        combined_init(2 * i) = x_comp_init(i)
        combined_init(2 * i + 1) = y_comp_init(i)
      }

      // hardware
      val axiMemSimConfig1= AxiMemorySimConfig(maxOutstandingReads = 3, maxOutstandingWrites = 8)
      val axiMemSimModel1 = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig1)
      val axiLite         = AxiLite4Driver(dut.io.topAxiLiteSlave, dut.clockDomain)

      axiMemSimModel1.start()
      axiMemSimModel1.memory.writeArray(0, vexGen(combined_init))
      axiMemSimModel1.memory.writeArray(0x400000, vexGen(combined_init))
      val (rb, cb, cb_length, edge) = edgeGen("./data/" + filename, tile_xy)
      axiMemSimModel1.memory.writeArray(0x800000, edge)

      // memory dbg
      mem_dbg(dbg_option, axiMemSimModel1)

      dut.clockDomain.waitSampling(200)

      // sbm initialization
      val ai_init = 0.0
      val ai_incr = 1.0 / num_iter
      val xi      = 0.7 / sqrt(matrix_size)
      // sbm initialization
      sbm_init(axiLite, cb , rb, cb_length, ai_init, ai_incr, xi )

      // start
      sbm_start(axiLite)

      @volatile var timeoutOccurred = false

      val timeout_thread = fork {
        dut.clockDomain.waitSampling(20000)
        timeoutOccurred = true
        if (!dut.io.done.toBoolean) {
          simFailure("Simulation timed out")
        }
      }

      if (simulator == "Verilator") {

        val JX_dbg_thread  = fork {
          var previous_busy = dut.io.update_busy.toBoolean
          while (!timeoutOccurred) {
            dut.clockDomain.waitSampling()
            val current_busy = dut.io.update_busy.toBoolean
            if (previous_busy && !current_busy) {
              val update_mem_values = (0 until 16).map(i => dut.pe_top.update_mem.getBigInt(i))
              for (i <- 0 until 16) {
                var update_mem_bits = update_mem_values(i).toBigInt
                for (j <- 0 until 32) {
                  val raw_update_mem_value = update_mem_bits  & ((1 << spmv_w) - 1)
                  var update_mem_value = if ((raw_update_mem_value & 0x800000) != 0) {
                    raw_update_mem_value | 0xFF000000 // 符号扩展到 32 位
                  } else {
                    raw_update_mem_value & 0xFFFFFF // 如果值为正，掩码为 24 位
                  }
                  val JX_dbg_value = JX_dbg(i * 32 + j)
                  assert(update_mem_value == JX_dbg_value, s"Mismatch at index ${(i * 32 + j)}: update_mem_value = $update_mem_value, JX_dbg_value = $JX_dbg_value")
                  update_mem_bits = update_mem_bits >> spmv_w
                }
              }
            }
            previous_busy = current_busy
          }
        }

        val partial_sum_dbg_thread1  = fork {
          val pe_num = 0
          var previous_busy = dut.pe_top.io.pe_busy.apply(pe_num).toBoolean
          var done = false
          while (!timeoutOccurred && !done) {
            dut.clockDomain.waitSampling()
            val current_busy = dut.pe_top.io.pe_busy.apply(pe_num).toBoolean
            if (previous_busy && !current_busy) {
              val mem_handler = dut.pe_top.pe_update_reg.apply(pe_num)
              for (i <- 0 until 8) {
                for (j <- 0 until 64) {
                  val raw_partial_mem_value = mem_handler.update_reg(i)(j).toBigInt
                  var partial_mem_value = if ((raw_partial_mem_value & 0x800000) != 0) {
                    raw_partial_mem_value | 0xFF000000 // 符号扩展到 32 位
                  } else {
                    raw_partial_mem_value & 0xFFFFFF // 如果值为正，掩码为 24 位
                  }
                  partial_mem_value = partial_mem_value.toInt
                  val partial_dbg       = partial_sum_dbg1(i*64+j).toInt
                  assert(partial_mem_value == partial_dbg, s"PE ${pe_num} Mismatch at index ${(i*64+j)}: partial_mem_value = $partial_mem_value, partial_value = $partial_dbg")
                }
              }
              done = true
            }
            previous_busy = current_busy
          }
        }

        val partial_sum_dbg_thread2  = fork {
          val pe_num = 1
          var previous_busy = dut.pe_top.io.pe_busy.apply(pe_num).toBoolean
          var done = false
          while (!timeoutOccurred && !done) {
            dut.clockDomain.waitSampling()
            val current_busy = dut.pe_top.io.pe_busy.apply(pe_num).toBoolean
            if (previous_busy && !current_busy) {
              val mem_handler = dut.pe_top.pe_update_reg.apply(pe_num)
              for (i <- 0 until 8) {
                for (j <- 0 until 64) {
                  val raw_partial_mem_value = mem_handler.update_reg(i)(j).toBigInt
                  var partial_mem_value = if ((raw_partial_mem_value & 0x800000) != 0) {
                    raw_partial_mem_value | 0xFF000000 // 符号扩展到 32 位
                  } else {
                    raw_partial_mem_value & 0xFFFFFF // 如果值为正，掩码为 24 位
                  }
                  partial_mem_value = partial_mem_value.toInt
                  val partial_dbg       = partial_sum_dbg2(i*64+j).toInt
                  assert(partial_mem_value == partial_dbg, s"PE ${pe_num} Mismatch at index ${(i*64+j)}: partial_mem_value = $partial_mem_value, partial_value = $partial_dbg")
                }
              }
              done = true
            }

            previous_busy = current_busy
          }
        }

        JX_dbg_thread.join()
        partial_sum_dbg_thread1.join()
        partial_sum_dbg_thread2.join()
      }

      timeout_thread.join()
    }
  }
}


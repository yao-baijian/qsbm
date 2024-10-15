import disp._
import spinal.core.sim._
import spinal.core._
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver
import test.qSBMTop
import scala.collection.mutable.{Seq, _}
import scala.sys.process._
import scala.math._

class TopSim extends TestBase {

  object MySpinalConfig extends SpinalConfig(

    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = ASYNC,
      resetActiveLevel = HIGH
    ),
    defaultClockDomainFrequency = FixedFrequency(300 MHz),
    targetDirectory     = "fpga/target",
    oneFilePerComponent = false
  )

  val simConfig     = SpinalSimConfig(_spinalConfig = MySpinalConfig)
  val simulator     = "Verilator"
  val compiled      = simulator match {
    case "Verilator" =>
      simConfig
        .workspacePath("build/VsimWorkspace")
        .withWave
        .compile(qSBMTop())
    case "Iverilog" =>
      simConfig
        .workspacePath("build/IsimWorkspace")
        .withWave
        .withIVerilog
        .compile(qSBMTop())
    case "Xsim" =>
      simConfig
        .workspacePath("build/XsimWorkspace")
        .withWave
        .withXSim
//        .withXilinxDevice("xcu280-fsvh2892-2L-e")
        .withXilinxDevice("xczu7ev-ffvc1156-2-e")
        .compile(qSBMTop())
    case _ =>
      throw new IllegalArgumentException("Unsupported simulator")
  }

  test("TopSim") {
    compiled
      .doSim { dut =>
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

      val ai_init = 1.0
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

        val x_y_comp_dbg_thread = fork {
          var previous_busy = dut.io.ge_busy.toBoolean

          while (!timeoutOccurred) {
            dut.clockDomain.waitSampling()
            val current_busy = dut.io.ge_busy.toBoolean

            // 检测下降沿
            if (previous_busy && !current_busy) {
              val x_y_comp_result = axiMemSimModel1.memory.readArray(0, combined_init.length)
              val x_comp_result = new Array[Byte](x_comp_init.length)
              val y_comp_result = new Array[Byte](x_comp_init.length)

              for (i <- x_comp_init.indices) {
                x_comp_result(i) = combined_init(2 * i)
                y_comp_result(i) = combined_init(2 * i + 1)
              }
              scoreboard("y_comp", y_comp_result, x_comp_dbg, dbg_iter)
              scoreboard("x_comp", x_comp_result, x_comp_dbg, dbg_iter)
            }
            previous_busy = current_busy
          }
        }

        x_y_comp_dbg_thread.join()
      }

      timeout_thread.join()
    }
  }
}

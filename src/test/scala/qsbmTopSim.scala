import dispatcher._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver
import test.SboomTop

import java.io._
import java.lang.Long.{parseLong, parseUnsignedLong}
import scala.collection.mutable.{Seq, _}
import scala.io.Source
import scala.math._
import scala.sys.process._

class qsbmTopSim extends AnyFunSuite {

  object MySpinalConfig extends SpinalConfig(

    defaultConfigForClockDomains = ClockDomainConfig(
      resetKind = ASYNC,
      resetActiveLevel = HIGH
    ),
    defaultClockDomainFrequency = FixedFrequency(300 MHz),
    targetDirectory = "fpga/target",
    oneFilePerComponent = false
  )

  val ipDir         = "fpga/ip"
  val xciSourcePaths = ArrayBuffer(
    new File(ipDir).listFiles().map(ipDir + "/" + _.getName) :_*
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

  val typ           = "scaleup"
  val num_iter      = 100
  val cmp_type      = "bsb"
  val filename      = "Gset/G34"
  val bestknown     = 2054
  val RB_length     = 512.0
  val tile_xy       = 64
  val dbg_iter      = 0

  val firstLine     = Source.fromFile("data/" + filename).getLines().next()
  val matrix_size   = firstLine.split(" ")(0).toInt

  val vex_a_base    = 0x0
  val vex_b_base    = 0x400000
  val edge_base     = 0x800000
  val RB_max        = ceil(matrix_size / RB_length).toInt

  val spmv_w        = 24
  val dbg_option    = false

  test("qsbmTopSim") {
    compiled.doSim { dut =>
      dut.clockDomain.forkStimulus(100)

      // algorithm
      val result = Seq("python", "quantization/spinal_test.py",
      filename,
      bestknown.toString,
      cmp_type,
      num_iter.toString,
      dbg_iter.toString,
      typ).!!

      val lines = result.split("\n")
      val x_comp_init   = lines(0).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val y_comp_init   = lines(1).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val JX_dbg        = lines(2).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toInt)
      val x_comp_dbg    = lines(3).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val y_comp_dbg    = lines(4).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toInt)
      val partial_sum_dbg = lines(5).split(",").map(_.toFloat)
      val qsb_energy    = lines(6).split(",").map(_.toFloat)

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
      sbm_init(axiLite, cb , rb, cb_length)

      // start
      sbm_start(axiLite)

      @volatile var timeoutOccurred = false

      val timeout_thread = fork {
        dut.clockDomain.waitSampling(20000)
        timeoutOccurred = true
        if (!dut.io.done.toBoolean) {
          simFailure("Simulation timed out")
        }
        println(s"qsb_energy: ${qsb_energy.mkString(", ")}")
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
                  val update_mem_value = update_mem_bits  & ((1 << spmv_w) - 1)
                  val JX_dbg_value = JX_dbg(i * 32 + j)
                  assert(update_mem_value == JX_dbg_value, s"Mismatch at index ${(i * 32 + j)}: update_mem_value = $update_mem_value, JX_dbg_value = $JX_dbg_value")
                  update_mem_bits = update_mem_bits >> (j * spmv_w)
                }
              }
            }
            previous_busy = current_busy
          }
        }

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
              scoreboard("x_comp", x_comp_result, x_comp_dbg, dbg_iter)
              scoreboard("y_comp", y_comp_result, x_comp_dbg, dbg_iter)
            }
            previous_busy = current_busy
          }
        }

        val partial_sum_dbg_thread  = fork {
          var previous_busy = dut.pe_top.io.pe_busy.apply(0).toBoolean
          var done = false
          while (!timeoutOccurred && !done) {
            dut.clockDomain.waitSampling()
            val current_busy = dut.pe_top.io.pe_busy.apply(0).toBoolean
            if (previous_busy && !current_busy) {
              val mem_handler = dut.pe_top.pe_update_reg.apply(0)
              for (i <- 0 until 8) {
                for (j <- 0 until 64) {
                  val raw_partial_mem_value = mem_handler.update_reg(i)(j).toBigInt
                  var partial_mem_value = if ((raw_partial_mem_value & 0x800000) != 0) {
                    raw_partial_mem_value | 0xFF000000 // 符号扩展到 32 位
                  } else {
                    raw_partial_mem_value & 0xFFFFFF // 如果值为正，掩码为 24 位
                  }
                  partial_mem_value = partial_mem_value.toInt
                  val partial_dbg       = partial_sum_dbg(i*64+j).toInt
                  assert(partial_mem_value == partial_dbg, s"Mismatch at index ${(i*64+j)}: partial_mem_value = $partial_mem_value, partial_value = $partial_dbg")
                }
              }
              done = true
            }

            previous_busy = current_busy
          }
        }

        JX_dbg_thread.join()
        x_y_comp_dbg_thread.join()
        partial_sum_dbg_thread.join()
      }

      timeout_thread.join()
    }
  }

  def qsb_python_thread (): Unit = {
  }

  def sbm_init(axi_driver : AxiLite4Driver, cb : Int, rb : Int, cb_length : Int): Unit = {
    axi_driver.write(0x0C, num_iter)    // 1000 iteration
    axi_driver.write(0x10, matrix_size) // matrix size 2000
    axi_driver.write(0x14, tile_xy)     // tile 64
    axi_driver.write(0x18, BigInt(ceil(matrix_size / tile_xy).toLong))    // max CB number = 2000 / 64 = 32
    axi_driver.write(0x40, RB_max)    // max RB number = 2000 / 512 = 4
    axi_driver.write(0x1C, cb)      // CB init
    axi_driver.write(0x20, rb)      // RB init
    axi_driver.write(0x44, cb_length)      // RB init
    axi_driver.write(0x24, 0)      // ai init
    axi_driver.write(0x28, 1)      // ai incr
    axi_driver.write(0x2C, 1)      // xi
    axi_driver.write(0x30, 16)     // dt
    axi_driver.write(0x34, vex_a_base)       // vex_a_base
    axi_driver.write(0x38, vex_b_base)       // vex_b_base
    axi_driver.write(0x3C, edge_base)        // edge_base
  }

  def sbm_start(axi_driver : AxiLite4Driver): Unit = {
    axi_driver.write(0x0, 1)
    axi_driver.write(0x0, 0)
  }

  def sbm_srst(axi_driver : AxiLite4Driver): Unit = {
    axi_driver.write(0x4, 1)
    axi_driver.write(0x4, 0)
  }

  def mem_dbg(dbg: Boolean, mem: AxiMemorySim): Unit = {
    if (dbg) {
      log_dbg(dbg_option, ("vex_all", mem.memory.readArray(0x0, 64)))
      log_dbg(dbg_option, ("vex1", mem.memory.readArray(0x0, 16)))
      log_dbg(dbg_option, ("vex2", mem.memory.readArray(0x10, 16)))
      log_dbg(dbg_option, ("edge_dbg", mem.memory.readArray(0x800000, 300)))
    }
  }

  def printNonZeroCounts(array: Array[Int], lineSize: Int): Unit = {
    for (i <- array.indices by lineSize) {
      val nonZeroCount = array.slice(i, i + lineSize).count(_ != 0)
      println(s"Line ${i / lineSize + 1}: $nonZeroCount non-zero values")
    }
  }

  def log_dbg(dbg: Boolean, namesAndTargets: (String, Any)*): Unit = {
    if (dbg) {
      namesAndTargets.foreach { case (name, target) =>
        println(s"name: $name")
        target match {
          case arr: Array[Byte] =>
            arr.zipWithIndex.foreach { case (value, i) =>
              if (i % 16 == 0 && i != 0) println()
              print(f"$value%02x ")
            }
            println()
          case _ =>
            println(s"target: $target")
        }
      }
    }
  }

  def scoreboard(name: String, target: Array[Byte], scoreboard: Array[Byte], sample_time: Int): Unit = {
    if (target.sameElements(scoreboard)) {
      println("数据比对成功！")
    } else {
      println("数据比对失败！")
      println(s"sample iteration: ${sample_time.toString}")
      println(s"${name} target length: ${target.length}")
      println(s"${name} scoreboard length: ${scoreboard.length}")
      println(s"${name} target: ${target.mkString(", ")}")
      println(s"${name} scoreboard: ${scoreboard.mkString(", ")}")
    }
  }

  def vexGen(vexValues: Array[Byte]) = {
    val fos = new FileOutputStream("build/vertex.bin")
    val dos = new DataOutputStream(fos)
    for (d <- vexValues) {
      dos.write(d)
    }
    dos.close()
    vexValues
  }

  def edgeGen(filename: String, tile_xy: Int) = {

    val firstLine = Source.fromFile(filename).getLines().next()
    val firFields = firstLine.split(' ')
    val arrayWidth = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / tile_xy).toInt
    //put a queue inside each block
    val blocks = Array.ofDim[ArrayBuffer[Byte]](arrayWidth, arrayWidth)

    for (row <- 0 until arrayWidth) {
      for (col <- 0 until (arrayWidth)) {
        blocks(row)(col) = ArrayBuffer[Byte]()
      }
    }

    val remainLines = Source.fromFile(filename).getLines().drop(1)

    for (line <- remainLines) {

      val row   = parseUnsignedLong(line.split(' ')(0)) - 1
      val col   = parseUnsignedLong(line.split(' ')(1)) - 1
      // J = -J
      val value = -parseLong(line.split(' ')(2))

      //  edge     = | row : col : value | = | 6 : 6 : 4 |    <--->     | 8 : 8 |
      //  val edge = ((row - 1) & 0x3F << 10) | (col - 1 & 0x3F << 6 ) | value & ((1 << 4) - 1)
      val edge    = ((row & 0x3F) << 10) | ((col & 0x3F) << 4) | value & 0xF
      val edge_t  = ((col & 0x3F) << 10) | ((row & 0x3F) << 4) | value & 0xF


      val edgeBytes = ArrayBuffer[Byte]( edge.toByte, (edge >>> 8).toByte)
      val edge_t_Bytes = ArrayBuffer[Byte]( edge_t.toByte, (edge_t >>> 8).toByte)

      // J = (J.T + J)
      // TODO need check here
      val block_row = (row / tile_xy).toInt
      val block_col = (col / tile_xy).toInt
      blocks(block_row)(block_col) ++= edgeBytes
      blocks(block_col)(block_row) ++= edge_t_Bytes
    }

    val edges_new           = ArrayBuffer[Byte]()
    val CB_last_non_empty_tile  = Queue[Int]()
    val RB_last_non_empty_CB    = Queue[Int]()
    val CB_length           = Queue[Int]()
    val CB_list             = Queue[Int]()
    val RB_list             = Queue[Int]()
    var non_empty_tile      = false
    var non_empty_CB        = false
    var last_non_empty_tile = 0
    var last_non_empty_CB   = 0
    var RB_init             = 0
    var CB_init             = 0
    var next_CB             = 0
    var next_RB             = 0
    var next_CB_length      = 0
    var CB_length_128       = 0
    var CB_length_init      = 0

    // RB
    for (base <- 0 until  arrayWidth by Config().pe_thread) {
      // CB
      last_non_empty_CB = 0
      non_empty_CB = false

      for (col <- 0 until arrayWidth) {
        non_empty_tile = false
        CB_length_128  = 0
        for (offset <- 0 until Config().pe_thread) {
          if (blocks(base + offset)(col).nonEmpty) {
            last_non_empty_tile = offset + 1
            CB_length_128 += ceil((blocks(base + offset)(col).length + 6) / 16.0).toInt // 5 byte for header
            non_empty_tile = true
          }
        }

        if (non_empty_tile) {
          if (CB_length_128 % 4 == 0) {
            CB_length += CB_length_128 / 4 + 1
          } else {
            CB_length += floor(CB_length_128 / 4.0).toInt + 1
          }
          CB_list += col + 1
          CB_last_non_empty_tile += last_non_empty_tile
          non_empty_CB = true
          last_non_empty_CB = col + 1
        }
      }

      if (non_empty_CB) {
        RB_list += floor(base / 8).toInt + 1
        RB_last_non_empty_CB += last_non_empty_CB
      }
    }

    RB_init   = RB_list.dequeue()
    CB_init   = CB_list.dequeue()
    CB_length_init = CB_length.dequeue()

    RB_list.enqueue(RB_init)
    CB_list.enqueue(CB_init)
    CB_length.enqueue(CB_length_init)

    log_dbg(dbg_option, ("RB_init", RB_init), ("CB_init", CB_init) , ("CB_length", CB_length))

    for (base <- 0 until  arrayWidth by Config().pe_thread) {
      last_non_empty_CB = 0
      next_RB = 0
      for (col <- 0 until arrayWidth) {
        last_non_empty_tile = 0
        next_CB = 0
        next_CB_length = 0
        for (offset <- 0 until Config().pe_thread) {
          if (blocks(base + offset)(col).nonEmpty) {
            if (last_non_empty_CB == 0) { last_non_empty_CB  = RB_last_non_empty_CB.dequeue() }
            if (last_non_empty_tile == 0) { last_non_empty_tile  = CB_last_non_empty_tile.dequeue() }
            if (col + 1 == last_non_empty_CB && offset + 1 == last_non_empty_tile && next_RB == 0) {
              next_RB = RB_list.dequeue()
              log_dbg(dbg_option, ("next_RB", next_RB))
            }
            if (offset + 1 == last_non_empty_tile && next_CB == 0 ) {
              next_CB         = CB_list.dequeue()
              next_CB_length  = CB_length.dequeue()
            }
            // TODO next_CB_length can not handle matrix elements more that 32 * 256
            edges_new ++= ArrayBuffer[Byte] (0.toByte, (offset + 1).toByte, next_RB.toByte, next_CB.toByte, next_CB_length.toByte, 0.toByte)
            edges_new ++= blocks(base + offset)(col)

            if (edges_new.length % 16 != 0) {
              // padding to make a 128b packet
              for (i <- 0 until 16 - edges_new.length % 16) {
                edges_new ++= ArrayBuffer[Byte] (0.toByte)
              }
            }

            // pad zeros for 512b alignment
            if (last_non_empty_tile == offset + 1) {
              for (i <- 0 until 64 - edges_new.length % 64) {
                val zero512 = ArrayBuffer[Byte] (0.toByte)
                edges_new ++= zero512
              }
            }
          }
        }
      }
    }

    val fos = new FileOutputStream("build/edge.bin")
    val dos = new DataOutputStream(fos)
    for (d <- edges_new) {
      dos.write(d)
    }
    dos.close()

    (RB_init, CB_init, CB_length_init, edges_new.toArray)
  }
}

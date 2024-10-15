import java.io._
import java.lang.Long.{parseLong, parseUnsignedLong}
import scala.collection.mutable._
import scala.io.Source
import scala.math._
import org.scalatest.funsuite.AnyFunSuite
import spinal.lib.bus.amba4.axi.sim.AxiMemorySim
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver
import cfg._
import spinal.core.sim._

class TestBase extends AnyFunSuite {

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

  // Simulation API

  def sbm_init(axi_driver : AxiLite4Driver, cb : Int, rb : Int, cb_length : Int, ai_init : Double, ai_incr : Double, xi : Double): Unit = {
    axi_driver.write(0x0C, num_iter)
    axi_driver.write(0x10, matrix_size)
    axi_driver.write(0x14, tile_xy)
    axi_driver.write(0x18, BigInt(ceil(matrix_size / tile_xy).toLong))
    axi_driver.write(0x40, RB_max)
    axi_driver.write(0x1C, cb)
    axi_driver.write(0x20, rb)
    axi_driver.write(0x44, cb_length)
    axi_driver.write(0x24, floatToFixed(ai_init, 16, 16))
    axi_driver.write(0x28, floatToFixed(ai_incr, 16, 16))
    axi_driver.write(0x2C, floatToFixed(xi, 16, 16))
    axi_driver.write(0x30, 16)
    axi_driver.write(0x34, vex_a_base)
    axi_driver.write(0x38, vex_b_base)
    axi_driver.write(0x3C, edge_base)
  }

  def sbm_start(axi_driver : AxiLite4Driver): Unit = {
    axi_driver.write(0x0, 1)
    axi_driver.write(0x0, 0)
  }

  def sbm_srst(axi_driver : AxiLite4Driver): Unit = {
    axi_driver.write(0x4, 1)
    axi_driver.write(0x4, 0)
  }

  // Debug API

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
      println("Data compare success！")
    } else {
      println("Data compare fail！")
      println(s"sample iteration: ${sample_time.toString}")
      println(s"${name} target length: ${target.length}")
      println(s"${name} scoreboard length: ${scoreboard.length}")
      println(s"${name} target: ${target.mkString(", ")}")
      println(s"${name} scoreboard: ${scoreboard.mkString(", ")}")
      simFailure("Data Mismatch")
    }
  }

  // Preprocessing API

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

      val block_row = (row / tile_xy).toInt
      val block_col = (col / tile_xy).toInt

      val edgeBytes = ArrayBuffer[Byte]( edge.toByte, (edge >>> 8).toByte)
      val edge_t_Bytes = ArrayBuffer[Byte]( edge_t.toByte, (edge_t >>> 8).toByte)

      // J = (J.T + J)
      // TODO need check here

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
    for (base <- 0 until  arrayWidth by Config.pe_thread) {
      // CB
      last_non_empty_CB = 0
      non_empty_CB = false

      for (col <- 0 until arrayWidth) {
        non_empty_tile = false
        CB_length_128  = 0
        for (offset <- 0 until Config.pe_thread) {
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

    for (base <- 0 until  arrayWidth by Config.pe_thread) {
      last_non_empty_CB = 0
      next_RB = 0
      for (col <- 0 until arrayWidth) {
        last_non_empty_tile = 0
        next_CB = 0
        next_CB_length = 0
        for (offset <- 0 until Config.pe_thread) {
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

  // Math API

  def floatToFixed(value: Double, intBits: Int, fracBits: Int): Int = {

    val maxValue = (1 << (intBits + fracBits - 1)) - 1
    val minValue = -(1 << (intBits + fracBits - 1))

    if (value >= 0) {
      val scaleFactor = 1 << fracBits
      val fixedValue = (value * scaleFactor).toInt & 0xffffffff
      fixedValue
    } else {
      val scaleFactor = 1 << fracBits
      val fixedValue = (-value * scaleFactor).toInt | 0x80000000 + 1
      fixedValue
    }
  }
}


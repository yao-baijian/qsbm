import dispatcher.Dispatcher
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim.{SimConfig, _}
import spinal.lib._
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import spinal.lib.bus.amba4.axilite.sim.AxiLite4Driver
import dispatcher._

import java.io._

import java.io.{File, FileOutputStream}
import java.lang.Long.{parseLong, parseUnsignedLong}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.math._
import scala.sys.process._

class SboomTopTest extends AnyFunSuite {

  object MySpinalConfig extends SpinalConfig(
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC,resetActiveLevel = HIGH),
    targetDirectory = "fpga/target",
    oneFilePerComponent = false
  )
  
  val ipDir = "fpga/ip"

  val xciSourcePaths = ArrayBuffer(
    new File(ipDir).listFiles().map(ipDir + "/" + _.getName) :_*
  )

  val simConfig = SpinalSimConfig(_spinalConfig = MySpinalConfig)

  val compiled= simConfig
    .withWave
    .withXilinxDevice("xcu280-fsvh2892-2L-e")
    .withXSim
    .compile(SboomTop(Config()))

  val num_iter = 100
  val cmp_type = "bsb"
  val filename = "G34"
  val bestknown = 2054
  val matrix_size = 2000
  val tile_xy   = 64

  test("SboomTopTest"){
    compiled.doSim { dut =>
      dut.clockDomain.forkStimulus(100)

      val axiMemSimConfig1= AxiMemorySimConfig(maxOutstandingReads = 2, maxOutstandingWrites = 8)
      val axiMemSimModel1 = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig1)
      val axiLite         = AxiLite4Driver(dut.io.topAxiLiteSlave, dut.clockDomain)

      val result = Seq("python", "quantization/spinal_test.py",
        filename,
        bestknown.toString,
        cmp_type,
        num_iter.toString).!!

      val lines = result.split("\n")
      val vexValues = lines(0).split(",").map(_.trim).filter(_.matches("-?\\d+")).map(_.toByte)
      val resultValues = lines(1).split(",").map(_.toFloat)

      axiMemSimModel1.start()
      axiMemSimModel1.memory.writeArray(0, vexGen(vexValues))
      axiMemSimModel1.memory.writeArray(0x800000, edgeGen("./data/" + filename))

      dut.clockDomain.waitSampling(200)

      axiLite.write(0x0C, num_iter)   // 1000 iteration
      axiLite.write(0x10, matrix_size)   // matrix size 2000
      axiLite.write(0x14, tile_xy)     // tile 64
      axiLite.write(0x18, 32)     // max CB number = 2000 / 64 = 32
      // math.ceil(matrixSize.toFloat/blockSize).toInt
      axiLite.write(0x1C, 0)      // CB init
      axiLite.write(0x20, 0)      // RB init
      axiLite.write(0x24, 0)      // ai init
      axiLite.write(0x28, 1)      // ai incr
      axiLite.write(0x2C, 1)      // xi
      axiLite.write(0x30, 16)     // dt
      axiLite.write(0x40, 16)     // max RB number = 2000 / 512 = 4

      axiLite.write(0x34, 0)      // vex_a_base
      axiLite.write(0x38, 0x400000)     // vex_b_base
      axiLite.write(0x3C, 0x800000)     // edge_base

      // start
      axiLite.write(0x0, 1)

      dut.clockDomain.waitSampling(10000)
      
      // read finish flag
      axiLite.read(0x32)

      val vexValue = axiMemSimModel1.memory.readArray(0, vexValues.length)

      if (resultValues.sameElements(vexValue)) {
        println("数据比对成功！")
      } else {
        println("数据比对失败！")
      }

    }
  }

  def vexGen(vexValues:Array[Byte]) = {
    val vertexBuffer = ArrayBuffer[Byte]()
    for (i <- 0 until 128 * 16) {
      val num = vexValues(i % vexValues.length)
      vertexBuffer.append(num)
    }

    val fos = new FileOutputStream("build/vertex.bin")
    val dos = new DataOutputStream(fos)
    for (d <- vertexBuffer) {
      dos.write(d)
    }
    dos.close()

    vertexBuffer.toArray
  }

  //generate edges and corresponding indices
  def edgeGen(filename: String) = {
    val firstLine = Source.fromFile(filename).getLines().next()
    val firFields = firstLine.split(' ')
    val arrayWidth = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / 64).toInt
    //put a queue inside each block
    val blocks = Array.ofDim[mutable.Queue[ArrayBuffer[Byte]]](arrayWidth, arrayWidth)
    for (row <- 0 until arrayWidth) {
      for (col <- 0 until (arrayWidth)) {
        blocks(row)(col) = mutable.Queue[ArrayBuffer[Byte]]()
      }
    }
    val remainLines = Source.fromFile(filename).getLines().drop(1)
    for (line <- remainLines) {
      val row = parseUnsignedLong(line.split(' ')(0)) - 1
      val col = parseUnsignedLong(line.split(' ')(1)) - 1
      val value = parseLong(line.split(' ')(2))
      val edge = (row & ((1 << 6) - 1)) << 10 |
        (col & ((1 << 6) - 1)) << 6 |
        value & ((1 << 4) - 1)
      val edgeBytes = ArrayBuffer[Byte](
        edge.toByte,
        (edge >>> 8).toByte
      )

      val block_row = (row / 64).toInt
      val block_col = (col / 64).toInt
      blocks(block_row)(block_col).enqueue(edgeBytes)
      blocks(block_col)(block_row).enqueue(edgeBytes)
    } //put all the edges into all queues

    val edgesArrayBuffer = ArrayBuffer[ArrayBuffer[Byte]]()
    var flag = 0
    var transfer_128 = 0
    var edgeCnt = 0
    val goodIntervalBound = arrayWidth
    val remainder = arrayWidth % (Config().pe_thread)

    // To deal with all lines within good interval and all column blocks
    var blockEmpty = 0
    var bigLineBlockCnt = 0

    for (base <- 0 until (goodIntervalBound) by Config().pe_thread) {
      for (col <- 0 until arrayWidth) { //arrayWidth is the number of 64 * 64 blocks
        do {
          // 128bits conccatenation
          flag = 0
          for (offset <- 0 until Config().pe_thread) {
            if (blocks(base + offset)(col).nonEmpty) {
              blockEmpty = 0
              flag = flag + 1
              edgeCnt = edgeCnt + 1
              if(edgeCnt >0 && edgeCnt%8 == 0){
                transfer_128 = transfer_128 + 1
              }
              val edge = blocks(base + offset)(col).dequeue()
              edgesArrayBuffer.append(edge)
            }
          } // 128bits conccatenation and paddings
        } while (flag > 0) //  flag>0 means that there is no allZeros

        if(flag == 0){
//        println("transfer_128 and edgeCnt",transfer_128,edgeCnt)
          bigLineBlockCnt = bigLineBlockCnt + 1
          blockEmpty = blockEmpty + 1
          if(blockEmpty == 1){
            var edgePaddingTo128Remainder = edgeCnt % 8
            var edgeIndexPaddingByteNum = (8 - edgePaddingTo128Remainder)/2
//            println("edgePaddingTo128Remainder",edgePaddingTo128Remainder)
            if(edgePaddingTo128Remainder != 0){
              //padding to make a 128b packet
              for(i <- 0 until 8-edgePaddingTo128Remainder){
                val edge = ArrayBuffer.fill(Config().edgeByteLen)(0.toByte)
                edgeCnt = edgeCnt + 1
                edgesArrayBuffer.append(edge)
              }
              transfer_128 = transfer_128 + 1
            }

            //forced to add seperator with 128b all zeros
            for(i <- 0 until 8){
              val edge = ArrayBuffer.fill(Config().edgeByteLen)(0.toByte)
              edgeCnt = edgeCnt + 1
              edgesArrayBuffer.append(edge)
            }
            transfer_128 = transfer_128 + 1
            if(transfer_128 % 4 == 1){

              val edgePadding =  ArrayBuffer.fill(16 * 3)(0.toByte)
              transfer_128 = transfer_128 + 3
              edgesArrayBuffer.append(edgePadding)
            } else if(transfer_128 % 4 == 2){
              val edgePadding = ArrayBuffer.fill(16 * 2)(0.toByte)
              val indexPadding = ArrayBuffer.fill(2)(0.toByte)
              transfer_128 = transfer_128 + 2
              edgesArrayBuffer.append(edgePadding)
            } else if (transfer_128 % 4 == 3) {
              val edgePadding = ArrayBuffer.fill(16 * 1)(0.toByte)
              transfer_128 = transfer_128 + 1
              edgesArrayBuffer.append(edgePadding)
            }
          }

// bigLine End flag with additional 512'b0(equivalent to add another 512'b0 seperator after the first seperator)
          if(bigLineBlockCnt%32 == 0){
            val edgePadding =  ArrayBuffer.fill(16 * 4)(0.toByte)
            transfer_128 = transfer_128 + 4
            edgesArrayBuffer.append(edgePadding)
          }
        }
      }
    }

    //To deal with remaining blocks
    if (remainder > 0) {
      for (col <- 0 until arrayWidth) {
        do {
          flag = 0
          for (offset <- 0 until remainder) {
            if (blocks(goodIntervalBound + offset)(col).nonEmpty) {
              blockEmpty = 0
              flag = flag + 1
              edgeCnt = edgeCnt + 1
              if(edgeCnt>0 && edgeCnt%8 == 0){
                transfer_128 = transfer_128 + 1
              }
              val edge = blocks(goodIntervalBound)(col).dequeue()
              edgesArrayBuffer.append(edge)
            }
          }
        } while (flag > 0)

        if (flag == 0) {
          blockEmpty = blockEmpty + 1
          if(blockEmpty == 1){
            //          println("-------transfer_128----------",transfer_128)
            var edgePaddingTo128Remainder = edgeCnt % 8
            var edgeIndexPaddingByteNum = (8 - edgePaddingTo128Remainder)/2

            if(edgePaddingTo128Remainder != 0){
              //padding to make a 128b packet
              for(i <- 0 until 8-edgePaddingTo128Remainder){
                val edge = ArrayBuffer.fill(Config().edgeByteLen)(0.toByte)
                edgesArrayBuffer.append(edge)
              }
              transfer_128 = transfer_128 + 1
            }

            // add extra seperator with 128bit all zeros
            for(i <- 0 until 8){
              val edge = ArrayBuffer.fill(Config().edgeByteLen)(0.toByte)
              edgeCnt = edgeCnt + 1
              edgesArrayBuffer.append(edge)
            }

            transfer_128 = transfer_128 + 1

            if (transfer_128 % 4 == 1) {
              val padding = ArrayBuffer.fill(16 * 3)(0.toByte)
              transfer_128 = transfer_128 + 3
              edgesArrayBuffer.append(padding)
            } else if (transfer_128 % 4 == 2) {
              val padding = ArrayBuffer.fill(16 * 2)(0.toByte)
              transfer_128 = transfer_128 + 2
              edgesArrayBuffer.append(padding)
            } else if (transfer_128 % 4 == 3) {
              val padding = ArrayBuffer.fill(16 * 1)(0.toByte)
              transfer_128 = transfer_128 + 1
              edgesArrayBuffer.append(padding)
            }

          }

          // bigLine End flag with additional 512'b0(equivalent to add another 512'b0 seperator after the first seperator)
          if(bigLineBlockCnt%32 == 0){
            val edgePadding =  ArrayBuffer.fill(16 * 4)(0.toByte)
            transfer_128 = transfer_128 + 4
            edgesArrayBuffer.append(edgePadding)
          }
        }
      }
    }

    val dataArray = ArrayBuffer[Byte]()
    for (i <- edgesArrayBuffer.indices) {
      val innerArr = edgesArrayBuffer(i)
      for (j <- innerArr.indices) {
        dataArray.append(innerArr(j))
      }
    }

    val fos = new FileOutputStream("build/edge.bin")
    val dos = new DataOutputStream(fos)
    for (d <- dataArray) {
      dos.write(d.toByte)
    }
    dos.close()

    dataArray.toArray
  }
}

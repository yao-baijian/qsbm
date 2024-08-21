package dispatcher

import spinal.core.ClockDomain.FixedFrequency
import spinal.core.{ClockDomainConfig, False, IntToBuilder, SYNC, SpinalConfig}
import spinal.core.sim._
import spinal.sim._
import spinal.lib._
import org.scalatest.funsuite.AnyFunSuite
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import spinal.lib.sim.StreamDriver

import java.io.{File, FileOutputStream}
import java.lang.Long.{parseLong, parseUnsignedLong}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.math._
import scala.util.Random

object MySpinalConfig extends SpinalConfig(
//  defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = LOW),
//  oneFilePerComponent = false
//  removePruned = true
)

object MySimConfig extends  SpinalSimConfig(
  _spinalConfig = MySpinalConfig

)

class DispatcherTest extends AnyFunSuite {

  val compiled= SimConfig.withWave.compile(Dispatcher())
  //slave axi4 port
  //  val axiMemPort = Axi4(compiled.dut.axiConfig)
  val axiMemSimConfig = AxiMemorySimConfig()
  val axiMemSimModel = AxiMemorySim(compiled.dut.io.axiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig)

  def dataGen(): Array[Byte] = {
    val data = new Array[Byte](100)

    for (i <- 0 until 100) {
      data(i) = i.toByte
    }
    data
  }

  def vexGen() = {

    val random = new Random()
    val vertexBuffer = ArrayBuffer[Byte]()
    for (i <- 0 until 128 * 16) {

      val num = (random.nextInt(33) - 16).toByte
      vertexBuffer.append(num)

    }
    vertexBuffer.toArray

  }
  def edgeGen() = {
    val filename = "./data/G34"
    val firstLine = Source.fromFile(filename).getLines().next()
    val firFields = firstLine.split(' ')
    val width = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / 64).toInt
    val arrayWidth = width
    println(arrayWidth)
    //put a queue inside each block
    val blocks = Array.ofDim[mutable.Queue[ArrayBuffer[Byte]]](arrayWidth, arrayWidth)
    for (row <- 0 until arrayWidth) {
      for (col <- 0 until (arrayWidth)) {
        blocks(row)(col) = mutable.Queue[ArrayBuffer[Byte]]()
      }
    }
    //    val fileOutput = new FileOutputStream(new File("./dataSet/G1.bin"))
    val remainLines = Source.fromFile(filename).getLines().drop(1)
    for (line <- remainLines) {
      val row = parseUnsignedLong(line.split(' ')(0))-1
      val col = parseUnsignedLong(line.split(' ')(1))-1
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
    var block_empty = 0
    val goodIntervalBound = arrayWidth/PeConfig().peNumEachColumn * PeConfig().peNumEachColumn
    val remainder = arrayWidth%(PeConfig().peNumEachColumn)

    // To deal with all lines within good interval and all column blocks
    for (base <- 0 until (goodIntervalBound) by PeConfig().peNumEachColumn) {
      for (col <- 0 until arrayWidth) {
        do {
          flag = 0
          for (offset <- 0 until PeConfig().peNumEachColumn) {
            if (blocks(base + offset)(col).nonEmpty) {
              flag = flag + 1
              val edge = blocks(base + offset)(col).dequeue()
              //              println("edge size", edge.length)
              //               tempArray(offset) = edge
              edgesArrayBuffer.append(edge)
            } else {
              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
              //               tempArray(offset) = edge
              edgesArrayBuffer.append(edge)
            }
          }
        } while (flag > 0) //flag>0 means that there is no allZeros
      }
    }

    //To deal with remaining blocks
    if(remainder > 0){
      for (col <- 0 until arrayWidth) {
        do {
          flag = 0
          for (offset <- 0 until remainder) {
            if (blocks(goodIntervalBound + offset)(col).nonEmpty) {
              flag = flag + 1
              val edge = blocks(goodIntervalBound)(col).dequeue()
              edgesArrayBuffer.append(edge)
            } else {
              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
              //tempArray(offset) = edge
              edgesArrayBuffer.append(edge)
            }
          }
          // if there is no enough blocks, we need to add the extra blocks and edges to be the multiples of number of PEs
          for (offset <- remainder until PeConfig().peNumEachColumn) {
            val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
            edgesArrayBuffer.append(edge)
          }
        } while (flag > 0)
      }
    }

    //    println("edge_number", edgesArrayBuffer.mkString(","))
    val dataArray = ArrayBuffer[Byte]()
    for (i <- edgesArrayBuffer.indices) {
      val innerArr = edgesArrayBuffer(i)
      for (j <- innerArr.indices) {
        dataArray.append(innerArr(j))
      }
    }

    import java.io._
    val fos = new FileOutputStream("data.bin")
    val dos = new DataOutputStream(fos)
    for (d <- dataArray) {
      dos.write(d.toByte)
    }
    dos.close()
    println("dataArrayLen",dataArray.toArray.length)
    //    print(dataArray)
    dataArray.toArray
  }


  test("DispatcherTest"){
//    implicit val _ = "."

    compiled.doSim { dut =>

      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟

      val axiMemSimConfig = AxiMemorySimConfig()
      val axiMemSimModel = AxiMemorySim(compiled.dut.io.axiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig)
      axiMemSimModel.start()
      axiMemSimModel.memory.writeArray(0, vexGen())
      axiMemSimModel.memory.writeArray(4096, edgeGen())

//      val (streamDriver0, cmdQueue0) = StreamDriver.queue(dut.scheduleBoard.scheduleBoardPorts(0), dut.clockDomain)
//      cmdQueue0.enqueue { payload => payload.randomize() }
//      cmdQueue0.enqueue { payload => payload.randomize() }
//      cmdQueue0.enqueue { payload => payload.randomize() }
//
//      val (streamDriver1, cmdQueue1) = StreamDriver.queue(dut.scheduleBoard.scheduleBoardPorts(1), dut.clockDomain)
//      cmdQueue1.enqueue { payload => payload.randomize() }
//      cmdQueue1.enqueue { payload => payload.randomize() }
//      cmdQueue1.enqueue { payload => payload.randomize() }
//
//      val (streamDriver2, cmdQueue2) = StreamDriver.queue(dut.scheduleBoard.scheduleBoardPorts(2), dut.clockDomain)
//      cmdQueue2.enqueue { payload => payload.randomize() }
//      cmdQueue2.enqueue { payload => payload.randomize() }
//      cmdQueue2.enqueue { payload => payload.randomize() }
//
//      val (streamDriver3, cmdQueue3) = StreamDriver.queue(dut.scheduleBoard.scheduleBoardPorts(3), dut.clockDomain)
//      cmdQueue3.enqueue { payload => payload.randomize() }
//      cmdQueue3.enqueue { payload => payload.randomize() }
//      cmdQueue3.enqueue { payload => payload.randomize() }

//      dut.io.bigPeReadyFlagVec(0) #= false
//      dut.io.bigPeReadyFlagVec(1) #= false
//      dut.io.bigPeReadyFlagVec(2) #= false
//      dut.io.bigPeReadyFlagVec(3) #= false

      dut.clockDomain.waitSampling(count = 8)

//      dut.io.bigPeReadyFlagVec(3) #= true
      dut.clockDomain.waitSampling(count = 100)
//
//      dut.io.bigPeReadyFlagVec(1) #= true


      dut.clockDomain.waitSampling() //等一个时钟上升沿到来
      dut.clockDomain.waitSampling(100)

      val dataRead = axiMemSimModel.memory.readArray(0, 10)
      for (i <- 0 until 10) {
        println(dataRead(i))
      }

      dut.clockDomain.waitSampling(count = 10)

//      assert(200 == dut.io.prod.toLong ,"乘法模块出错！")
      dut.clockDomain.waitSampling(1000)

//    println(dut.io.prod.toLong)
//    println("hello")

    }
  }


}

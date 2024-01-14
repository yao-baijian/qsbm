import dispatcher.Dispatcher
import org.scalatest.funsuite.AnyFunSuite
import spinal.core._
import spinal.core.sim.{SimConfig, _}
import spinal.lib._
import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
import dispatcher._

import java.io.{File, FileOutputStream}
import java.lang.Long.{parseLong, parseUnsignedLong}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.math._
import scala.util.Random

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
  val xSimConfig = simConfig.copy(_workspacePath = "xSimWorkspace").withXSim.withXSimSourcesPaths(xciSourcePaths,ArrayBuffer(""))

  val compiled= simConfig.withWave.compile(SboomTop())
//  val compiled= xSimConfig.withWave.compile(SboomTop())
//  val axiMemSimConfig = AxiMemorySimConfig()
//  val axiMemSimModel = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig)

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

      val num = 1.toByte //(random.nextInt(33) - 16).toByte
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
    println("arrayWidth = ",arrayWidth)
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
    var block_empty = 0
    val goodIntervalBound = arrayWidth / PeConfig().peNumEachColumn * PeConfig().peNumEachColumn
    val remainder = arrayWidth % (PeConfig().peNumEachColumn)

    // To deal with all lines within good interval and all column blocks
    for (base <- 0 until (goodIntervalBound) by PeConfig().peNumEachColumn) {
      for (col <- 0 until arrayWidth) { //arrayWidth is the number of 64 * 64 blocks
        do {
          // 128bits conccatenation
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

            transfer_128 = transfer_128 + 1
            if(flag == 0){
              if(transfer_128 % 4 == 1){
                val padding =  ArrayBuffer.fill(16 * 3)(0.toByte)
                edgesArrayBuffer.append(padding)
              } else if(transfer_128 % 4 == 2){
                val padding = ArrayBuffer.fill(16 * 2)(0.toByte)
                edgesArrayBuffer.append(padding)
              } else if (transfer_128 % 4 == 3) {
                val padding = ArrayBuffer.fill(16 * 1)(0.toByte)
                edgesArrayBuffer.append(padding)
              }
            }
          }  // 128bits conccatenation and paddings
        } while (flag > 0) //  flag>0 means that there is no allZeros
      }
    }

    //To deal with remaining blocks
    if (remainder > 0) {
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
          transfer_128 = transfer_128 + 1
          if (flag == 0) {
            if (transfer_128 % 4 == 0) {
              val padding = ArrayBuffer.fill(16 * 3)(0.toByte)
              edgesArrayBuffer.append(padding)
            } else if (transfer_128 % 4 == 1) {
              val padding = ArrayBuffer.fill(16 * 2)(0.toByte)
              edgesArrayBuffer.append(padding)
            } else if (transfer_128 % 4 == 2) {
              val padding = ArrayBuffer.fill(16 * 1)(0.toByte)
              edgesArrayBuffer.append(padding)
            }
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
    println("dataArrayLen", dataArray.toArray.length)
//    print(dataArray)
    println("transfer_128",transfer_128)
    dataArray.toArray
  }

  test("SboomTopTest"){

    compiled.doSim { dut =>

      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟

      val axiMemSimConfig = AxiMemorySimConfig()
      val axiMemSimModel = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig)
      axiMemSimModel.start()
      axiMemSimModel.memory.writeArray(0, vexGen())
      axiMemSimModel.memory.writeArray(4096, edgeGen())

      dut.clockDomain.waitSampling(count = 8)
      dut.clockDomain.waitSampling(count = 100)

      val dataRead = axiMemSimModel.memory.readArray(0, 10)
      for (i <- 0 until 10) {
        println(dataRead(i))
      }
      dut.clockDomain.waitSampling(10000)

    }
  }

}



//package AXI4_DDR
//
//import org.scalatest.funsuite.AnyFunSuite
//import spinal.core._
//import spinal.core.sim.{SimConfig, _}
//import spinal.lib._
//import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
//
//import java.io.{File, FileOutputStream}
//import java.lang.Long.{parseLong, parseUnsignedLong}
//import scala.collection.mutable
//import scala.collection.mutable.ArrayBuffer
//import scala.io.Source
//import scala.math._
//import scala.util.Random
//import java.math.BigInteger
//import spinal.lib.bus.amba4.axilite.sim._
//
//object MySpinalConfig extends SpinalConfig(
//  defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = LOW),
//  oneFilePerComponent = false
//)
//
//object MySpinalSimConfig extends  SpinalSimConfig(
//  _spinalConfig = MySpinalConfig
//)
//
//class SbmSparseTopTest extends AnyFunSuite{
//
//
//  def randDataGen(): Array[Byte] = {
//    val data = new Array[Byte](5000)
//
//    for (i <- 0 until 5000) {
//      data(i) = (i%256).toByte
//    }
//    data
//  }
//
//  def vertexGen() = {
//
//    val random = new Random()
//    val vertexBuffer = ArrayBuffer[Byte]()
//    for(i <- 0 until AxiConConfig().graphSize ){
//
//      //      val num = (random.nextInt(33) - 16).toByte
//      val num = (i-100).toByte
//      vertexBuffer.append(num)
//
//    }
//    vertexBuffer.toArray
//
//  }
//
//  def testVexGen() = {
//
//    val vertexBuffer = ArrayBuffer[Byte]()
//    for (i <- 0 until 800) {
//
//      //      val num = (random.nextInt(33) - 16).toByte
//
//      var y = VertexArray.y_comp(i).toByte
//      vertexBuffer.append(y)
//      var x = VertexArray.x_comp(i).toByte
//      vertexBuffer.append(x)
//    }
//    for(j<-0 until 32){
//      //      var x = VertexArray.x_comp(i).toByte
//      vertexBuffer.append(0.toByte)
//      //      var y = VertexArray.y_comp(i).toByte
//      vertexBuffer.append(0.toByte)
//    }
//
//    print(vertexBuffer)
//    println(vertexBuffer.length)
//
//    import java.io._
//    val fos = new FileOutputStream("vertex.bin")
//    val dos = new DataOutputStream(fos)
//    for (d <- vertexBuffer) {
//      dos.write(d.toByte)
//    }
//    dos.close()
//
//    vertexBuffer.toArray
//
//  }
//
//  def edgeGen() = {
//
//    val filename = "./dataSet/G34"
//    val firstLine = Source.fromFile(filename).getLines().next()
//    val firFields = firstLine.split(' ')
//    //    val secondLine = Source.fromFile(filename).getLines().drop(1).next()
//    //    val secFields = secondLine.split(' ')
//    val width = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / 64).toInt
//    //    val cnt = (0 to 3).find((i => (i + width) % 4 == 0)).get
//    //    println("width,cnt",width,cnt)
//    val arrayWidth = width //+ cnt
//    println(arrayWidth)
//
//    //put a queue inside each block
//    val blocks = Array.ofDim[mutable.Queue[ArrayBuffer[Byte]]](arrayWidth, arrayWidth)
//    for (row <- 0 until arrayWidth) {
//      for (col <- 0 until (arrayWidth)) {
//        blocks(row)(col) = mutable.Queue[ArrayBuffer[Byte]]()
//      }
//    }
//
//    //    val fileOutput = new FileOutputStream(new File("./dataSet/G1.bin"))
//    val remainLines = Source.fromFile(filename).getLines().drop(1)
//
//    for (line <- remainLines) {
//
//      val row = parseUnsignedLong(line.split(' ')(0))-1
//      val col = parseUnsignedLong(line.split(' ')(1))-1
//      val value = parseLong(line.split(' ')(2))
//
//      val edge = (row & ((1 << 6) - 1)) << 10 |
//        (col & ((1 << 6) - 1)) << 6 |
//        value & ((1 << 4) - 1)
//
//      //      val edge2 = (col & ((1 << 10) - 1)) << 22 |
//      //        (row & ((1 << 10) - 1)) << 12 |
//      //        value & ((1 << 12) - 1)
//
//      val edgeBytes = ArrayBuffer[Byte](
//        //        (edge >>> 24).toByte,
//        //        (edge >>> 16).toByte,
//        //        (edge >>> 8).toByte,
//        //        edge.toByte
//
//        edge.toByte,
//        (edge >>> 8).toByte
//        //        (edge >>> 16).toByte,
//        //        (edge >>> 24).toByte
//      )
//
//      //      val edgeBytes2 = ArrayBuffer[Byte](
//      //        //        (edge >>> 24).toByte,
//      //        //        (edge >>> 16).toByte,
//      //        //        (edge >>> 8).toByte,
//      //        //        edge.toByte
//      //
//      //        edge2.toByte,
//      //        (edge2 >>> 8).toByte,
//      //        (edge2 >>> 16).toByte,
//      //        (edge2 >>> 24).toByte
//      //      )
//
//      val block_row = (row / 64).toInt
//      //      println("block_row",block_row)
//      val block_col = (col / 64).toInt
//      //      println("block_col",block_col)
//
//      blocks(block_row)(block_col).enqueue(edgeBytes)
//      blocks(block_col)(block_row).enqueue(edgeBytes)
//
//    } //put all the edges into all queues
//
//
//    //
//    val edgesArrayBuffer = ArrayBuffer[ArrayBuffer[Byte]]()
//    var flag = 0
//    var block_empty = 0
//
//    val goodIntervalBound = arrayWidth/AxiConConfig().peTotalNum * AxiConConfig().peTotalNum
//    val remainder = arrayWidth%4
//    // To deal with all lines within good interval and all column blocks
//    for (base <- 0 until (goodIntervalBound) by AxiConConfig().peTotalNum) {
//      for (col <- 0 until arrayWidth) {
//        //        println("goodIntervalBound",goodIntervalBound)
//        do {
//          flag = 0
//          for (offset <- 0 until  AxiConConfig().peTotalNum) {
//            if (blocks(base + offset)(col).nonEmpty) {
//              flag = flag + 1
//              val edge = blocks(base + offset)(col).dequeue()
//              //              println("edge size", edge.length)
//              //               tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
//            } else {
//              val edge = ArrayBuffer.fill(AxiConConfig().edgeByteLen)(0.toByte)
//              //               tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
//            }
//          }
//          if (flag == 0) {
//            block_empty = block_empty + 1
//          }
//          //           println("loop")
//        } while (flag > 0) //flag>0 means that there is no allZeros
//
//      }
//    }
//
//    //To deal with remaining blocks
//    if(remainder > 0){
//      for (col <- 0 until arrayWidth) {
//        do {
//          flag = 0
//          for (offset <- 0 until remainder) {
//            if (blocks(goodIntervalBound + offset)(col).nonEmpty) {
//              flag = flag + 1
//              val edge = blocks(goodIntervalBound)(col).dequeue()
//              //              println("edge size", edge.length)
//              //               tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
//            } else {
//              val edge = ArrayBuffer.fill(AxiConConfig().edgeByteLen)(0.toByte)
//              //tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
//            }
//          }
//
//          // if there is no enough blocks, we need to add the extra blocks and edges to be the multiples of number of PEs
//          for (offset <- remainder until AxiConConfig().peTotalNum) {
//            val edge = ArrayBuffer.fill(AxiConConfig().edgeByteLen)(0.toByte)
//            edgesArrayBuffer.append(edge)
//          }
//
//          if (flag == 0) {
//            block_empty = block_empty + 1
//          }
//        } while (flag > 0)
//      }
//    }
//
//
//    //    println("edge_number", edgesArrayBuffer.mkString(","))
//
//    val dataArray = ArrayBuffer[Byte]()
//    for (i <- edgesArrayBuffer.indices) {
//      val innerArr = edgesArrayBuffer(i)
//      for (j <- innerArr.indices) {
//        dataArray.append(innerArr(j))
//      }
//    }
//
//    import java.io._
//
//    val fos = new FileOutputStream("data.bin")
//    val dos = new DataOutputStream(fos)
//
//    for (d <- dataArray) {
//      dos.write(d.toByte)
//    }
//
//    dos.close()
//
//    println("dataArrayLen",dataArray.toArray.length)
//    //    print(dataArray)
//    dataArray.toArray
//
//  }
//
//
//  test("AXI4_DDR.SbmSparseTopTest"){
//
//    //    println("AxiConConfig().allZeroCntBoundVal",AxiConConfig().allZeroCntBoundVal)
//    //    print("edgeArray",edgeGen().mkString(","))
//    //    edgeGen()
//
//    val compiled = MySpinalSimConfig
//      //      .withXSim.withXilinxDevice("xc7z020clg484-1")
//      .withWave.compile(SbmSparseTop())
//
//    //
//    compiled.doSim { dut =>
//
//
//      //      SimTimeout(100000)
//      //      dut.io.read_flag #= true
//
//      val axiMemSimConfig = AxiMemorySimConfig(maxOutstandingReads = 1)
//      val axiMemSimModel = AxiMemorySim(compiled.dut.io.topAxiMemPort, compiled.dut.clockDomain, axiMemSimConfig)
//      //
//      dut.clockDomain.forkStimulus(period = 5000) // 5000ps at 200MHz
//      axiMemSimModel.start()
//      axiMemSimModel.memory.writeArray(0, vertexGen())
//      axiMemSimModel.memory.writeArray(33554432, edgeGen()) //32M base addr
//      dut.clockDomain.waitSampling(5)
//      //
//      val axiLite4Driver = AxiLite4Driver(compiled.dut.io.topAxiLiteSlave, compiled.dut.clockDomain)
//      axiLite4Driver.reset()
//
//      //configure the register file for some parameters inside
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 8)&0xffffffffL,  AxiConConfig().rowsInOneIter)
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 12)&0xffffffffL, AxiConConfig().blocksInOneRow)
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 16)&0xffffffffL, AxiConConfig().alphaNum)
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 20)&0xffffffffL, AxiConConfig().alphaChangeVal)
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 24)&0xffffffffL,AxiConConfig().xi)
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 28)&0xffffffffL,AxiConConfig().dt)
//
//
//      //start computation
//      axiLite4Driver.write((2 * 1024 * 1024 * 1024 + 0)&0xffffffffL, 1) //@ 2G = 0x80000000 address
//      //
//      dut.clockDomain.waitSampling(count = 500000)
//
//
//      //number of cycles ,and set the period before to 10 ps
//
//      //      while(true){
//      //
//      //        dut.clockDomain.waitSamplingWhere(dut.io.topAxiMemPort.w.valid.toBoolean && dut.axi4MemController.wrFsmCurrentState.toInt == 3)
//      //        var data = dut.io.topAxiMemPort.w.payload.data.toBigInt
//      //        var bytes = data.toByteArray // 将数据转换为字节数组
//      //        var paddedBytes = Array.fill[Byte](16)(0) // 填充长度为16的字节数组
//      //        var paddedData = (paddedBytes ++ bytes).takeRight(16)
//      //        var result = for (i <- 0 until paddedData.length by 4) yield {
//      //          paddedData.slice(i, i + 4).map("%02X".format(_)).mkString
//      //        }
//      //        println(result.mkString(", "))
//      //      }
//
//      //var bytes = data.toByteArray
//      //        var result = for (i <- 0 until bytes.length by 4) yield {
//      //          bytes.slice(i, i + 4).map("%02X".format(_)).mkString
//      //        }
//      //        println(result.mkString(", "))
//      //    }
//
//
//
//
//    }
//
//  }
//
//}

//      dut.clockDomain.waitSampling(count = 10)

//      val dataVexRead = axiMemSimModel.memory.readArray(0, 8)
//      dut.clockDomain.waitSamplingWhere(dut.axi4MemController.io.gotoDispatchVerRegFlag.toBoolean == true)

//      val dataEdgeRead = axiMemSimModel.memory.readArray(4096, 1024)
//      dut.clockDomain.waitSamplingWhere(dut.axi4MemController.io.gotoDispatchEdgeFlag.toBoolean == true)



//      val dataEdgeRead = axiMemSimModel.memory.readArray(4096, 100256)

//      for(i <- 0 until 100){
//        println(dataRead(i))
//      }
//      dut.clockDomain.waitSamplingWhere(dut.dispatcher.io.stateNotify.toInt == 0x02)
//      dut.clockDomain.waitSamplingWhere(dut.axi4MemController.io.gotoDispatchEdgeFlag == true)
//      val dataEdgeRead = axiMemSimModel.memory.readArray(4096, 1024)

//      for(i <- 0 until 100){
//        println(dataEdgeRead(i))
//      }
//      dut.clockDomain.waitSampling(count = 10)
////

//      dut.io.read_addr #= BigInt("0", 16)
//      dut.clockDomain.waitSamplingWhere(dut.io.topAxiMemPort.b.valid)
//
//      dut.clockDomain.waitSamplingWhere(dut.io.topAxiMemPort.b.valid.toBoolean)
//      val dataVexRead1 = axiMemSimModel.memory.readArray(8192, 512)
//      val arrByte = new Array[Byte](8)
//      for(i <- 0 until 512){
//
//        for(j<-0 until 8){
//          arrByte(j) = (dataVexRead1(i).toByte)
//        }
//
//
//      }
//


//      val dataVexRead2 = axiMemSimModel.memory.readArray(8192+512, 512)
//
//      for (i <- 0 until 512) {
//        println((8192+512+i, dataVexRead2(i)))
//      }
//      dut.clockDomain.waitSamplingWhere(dut.io.topAxiMemPort.b.valid.toBoolean)
//      val dataVexRead = axiMemSimModel.memory.readArray(8192+512*2, 512)
//
//      for (i <- 8192+512*2 until 8192+512*3) {
//        println((i, dataVexRead(i)))
//      }
//      dut.clockDomain.waitSamplingWhere(dut.io.topAxiMemPort.b.valid.toBoolean)
//      val dataVexRead = axiMemSimModel.memory.readArray(8192+512*3, 512)
//
//      for (i <- 0 until 512) {
//        println((i, dataVexRead(i)))
//      }
//      println("boundval", AxiConConfig().boundVal)

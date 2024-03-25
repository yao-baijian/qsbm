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
  //val xSimConfig = simConfig.copy(_workspacePath = "xSimWorkspace".withXSim.withXSimSourcesPaths(xciSource)Paths,ArrayBuffer(""))

  val compiled= simConfig
    .withWave
    .withXilinxDevice("xczu7ev-ffvc1156-2-e")
    .withXSim
    .compile(SboomTop())

//  val compiled= simConfig.withWave.compile(SboomTop())
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

  //generate edges and corresponding indices
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
    val edgeIndexBuffer = ArrayBuffer[Byte]()
    var edgeIndexByte:Int = 0
    var flag = 0
    var transfer_128 = 0
    var block_empty = 0
    var edgeCnt = 0
    val goodIntervalBound = arrayWidth / PeConfig().peNumEachColumn * PeConfig().peNumEachColumn
    val remainder = arrayWidth % (PeConfig().peNumEachColumn)

    // To deal with all lines within good interval and all column blocks
    var blockEmpty = 0
    for (base <- 0 until (goodIntervalBound) by PeConfig().peNumEachColumn) {
      for (col <- 0 until arrayWidth) { //arrayWidth is the number of 64 * 64 blocks
        do {
          // 128bits conccatenation
//          edgeIndexByte = 0
          flag = 0
          for (offset <- 0 until PeConfig().peNumEachColumn) {
            if (blocks(base + offset)(col).nonEmpty) {
              blockEmpty = 0
              flag = flag + 1
              edgeCnt = edgeCnt + 1
              if(edgeCnt >0 && edgeCnt%8 == 0){
                transfer_128 = transfer_128 + 1
              }

              //generating edge indices
              if(edgeCnt%2 == 1){
                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte)<<4)
              }
              else{
                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte))
                edgeIndexBuffer.append(edgeIndexByte.toByte)
                edgeIndexByte = 0
              }
              //put the edges into the buffer
              val edge = blocks(base + offset)(col).dequeue()
              edgesArrayBuffer.append(edge)
            } else {
              // Do nothing
//              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
              //               tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
            }
          } // 128bits conccatenation and paddings
        } while (flag > 0) //  flag>0 means that there is no allZeros

        if(flag == 0){
          println("transfer_128 and edgeCnt",transfer_128,edgeCnt)
          blockEmpty = blockEmpty + 1
          if(blockEmpty == 1){
            var edgePaddingTo128Remainder = edgeCnt % 8
            var edgeIndexPaddingByteNum = (8 - edgePaddingTo128Remainder)/2
            println("edgePaddingTo128Remainder",edgePaddingTo128Remainder)
            if(edgePaddingTo128Remainder != 0){
              //padding to make a 128b packet
              for(i <- 0 until 8-edgePaddingTo128Remainder){
                val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
                edgeCnt = edgeCnt + 1
                edgesArrayBuffer.append(edge)
              }
              //edgeIndexPadding to make 32b index packet
              for(i <- 0 until edgeIndexPaddingByteNum){
                edgeIndexBuffer.append(0.toByte)
              }
              transfer_128 = transfer_128 + 1
            }

            //seperator added with 128b all zeros
            for(i <- 0 until 8){
              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
              edgeCnt = edgeCnt + 1
              edgesArrayBuffer.append(edge)
            }
            //edgeIndexPadding to make 32b index packet
            for(i <- 0 until 4){
              edgeIndexBuffer.append(0.toByte)
            }
            transfer_128 = transfer_128 + 1
            println("debug transfer_128",transfer_128)

            //judge the number of 128b transfers
            if(transfer_128 % 4 == 1){
              val edgePadding =  ArrayBuffer.fill(16 * 3)(0.toByte)
              transfer_128 = transfer_128 + 3
              edgesArrayBuffer.append(edgePadding)
              //edgeIndex Padding
              for(i <- 0 until 3*4){ //for each 128b edge packet, there are 4Bytes needed for index
                edgeIndexBuffer.append(0.toByte)
              }
            } else if(transfer_128 % 4 == 2){
              val edgePadding = ArrayBuffer.fill(16 * 2)(0.toByte)
              val indexPadding = ArrayBuffer.fill(2)(0.toByte)
              transfer_128 = transfer_128 + 2
              edgesArrayBuffer.append(edgePadding)
              //edgeIndex Padding
              for(i <- 0 until 2*4){
                edgeIndexBuffer.append(0.toByte)
              }
            } else if (transfer_128 % 4 == 3) {
              val edgePadding = ArrayBuffer.fill(16 * 1)(0.toByte)
              transfer_128 = transfer_128 + 1
              edgesArrayBuffer.append(edgePadding)
              //edgeIndex Padding
              for(i <- 0 until 1*4){
                edgeIndexBuffer.append(0.toByte)
              }
            }

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

              //generating edge indices
              if(edgeCnt%2 == 1){
                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte)<<4)
              }
              else{
                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte))
                edgeIndexBuffer.append(edgeIndexByte.toByte)
                edgeIndexByte = 0
              }
              val edge = blocks(goodIntervalBound)(col).dequeue()
              edgesArrayBuffer.append(edge)
            } else {
              //Do Nothing
//              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//              //tempArray(offset) = edge
//              edgesArrayBuffer.append(edge)
            }
          }
          // if there is no enough blocks, we need to add the extra blocks and edges to be the multiples of number of PEs
//          for (offset <- remainder until PeConfig().peNumEachColumn) {
//            val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//            edgesArrayBuffer.append(edge)
//          }
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
                val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
                edgesArrayBuffer.append(edge)
              }
              //edgeIndexPadding to make 32b index packet
              for(i <- 0 until edgeIndexPaddingByteNum){
                edgeIndexBuffer.append(0.toByte)
              }
              transfer_128 = transfer_128 + 1
            }

            // add extra seperator with 128bit all zeros
            for(i <- 0 until 8){
              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
              edgeCnt = edgeCnt + 1
              edgesArrayBuffer.append(edge)
            }
            //edgeIndexPadding to make 32b index packet
            for(i <- 0 until 4){
              edgeIndexBuffer.append(0.toByte)
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
        }
      }
    }

    //    println("edge_number", edgesArrayBuffer.mkString(","))
    //*************************************generate edge bin file ****************************//
    val dataArray = ArrayBuffer[Byte]()
    for (i <- edgesArrayBuffer.indices) {
      val innerArr = edgesArrayBuffer(i)
      for (j <- innerArr.indices) {
        dataArray.append(innerArr(j))
      }
    }

    import java.io._
    val fos = new FileOutputStream("edge.bin")
    val dos = new DataOutputStream(fos)
    for (d <- dataArray) {
      dos.write(d.toByte)
    }
    dos.close()

    //************************************generate edgeIndex bin file*************************************//
//    val edgeIndexArray = ArrayBuffer[Byte]()
//    for (i <- edgeIndexBuffer.indices) {
//      val innerArr = edgesArrayBuffer(i)
//      for (j <- innerArr.indices) {
//        dataArray.append(innerArr(j))
//      }
//    }
//
    val fos1 = new FileOutputStream("edgeIndex.bin")
    val dos1 = new DataOutputStream(fos1)
    for (d <- edgeIndexBuffer) {
      dos1.write(d.toByte)
    }
    dos1.close()

    println("dataArrayLen", dataArray.toArray.length)
//    print(edgeIndexBuffer.toArray.toString)
//    print(dataArray)
//    println("transfer_128",transfer_128)
    (edgeIndexBuffer.toArray,dataArray.toArray)
  }

  test("SboomTopTest"){

    compiled.doSim { dut =>

      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟

      //axi4 port1 for vex&edges
      val axiMemSimConfig1 = AxiMemorySimConfig(maxOutstandingReads = 1,maxOutstandingWrites = 1)
      val axiMemSimModel1 = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig1)

      // axi4 port2 for indices
      val axiMemSimConfig2 = AxiMemorySimConfig(maxOutstandingReads = 1,maxOutstandingWrites = 1)
      val axiMemSimModel2 = AxiMemorySim(compiled.dut.io.topAxiEdgeIndexPort, compiled.dut.clockDomain, axiMemSimConfig2)

      axiMemSimModel1.start()
      axiMemSimModel2.start()

      axiMemSimModel1.memory.writeArray(0, vexGen())
      val (edgeIndex, edge) = edgeGen()
//      axiMemSimModel1.memory.writeArray(0x400000,edgeIndex)
      axiMemSimModel2.memory.writeArray(0x400000,edgeIndex)
      axiMemSimModel1.memory.writeArray(0x800000, edge)



      dut.clockDomain.waitSampling(count = 8)
      dut.clockDomain.waitSampling(count = 100)

      val dataRead = axiMemSimModel1.memory.readArray(0, 10)
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

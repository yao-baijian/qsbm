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
import scala.util.control.Breaks

class SboomTopTest extends AnyFunSuite {

  object MySpinalConfig extends SpinalConfig(
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC,resetActiveLevel = HIGH),
    targetDirectory = "fpga/target",
    oneFilePerComponent = false
  )

  val simConfig = SpinalSimConfig(_spinalConfig = MySpinalConfig)
  //val xSimConfig = simConfig.copy(_workspacePath = "xSimWorkspace".withXSim.withXSimSourcesPaths(xciSource)Paths,ArrayBuffer(""))

  //    val compiled= simConfig.withWave.withXilinxDevice("xczu7ev-ffvc1156-2-e").withXSim.compile(SboomTop())
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

    //generate edges and corresponding indices
    def edgeGen() = {
      val filename = "./data/G34"
      val firstLine = Source.fromFile(filename).getLines().next()
      val firFields = firstLine.split(' ')
      val width = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / 64).toInt
      val arrayWidth = width
  //    println("arrayWidth = ",arrayWidth)
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
      var bigLineBlockCnt = 0
      var jumpStep1 = 0

      for (base <- 0 until (goodIntervalBound) by PeConfig().peNumEachColumn) {
        for (col <- 0 until arrayWidth) { //arrayWidth is the number of 64 * 64 blocks
          jumpStep1 = 0
          // firsr walk through the big column and find the last one with valid data
          var lastNonEmptyBlockNum = 0
          for(offset <- 0 until PeConfig().peNumEachColumn){
            if(blocks(base + offset)(col).nonEmpty){
              lastNonEmptyBlockNum = offset
            }
          }
          // add the edges in respective small blocks in one big column
          for (offset <- 0 until PeConfig().peNumEachColumn) {
            if(offset == lastNonEmptyBlockNum ){
              //look ahead to calculate how many jumps needed
              //break implementatioin in scala
              val out1 = new Breaks
              val inner1 = new Breaks
              out1.breakable {
                for (i <- col+1 until arrayWidth) {
                  inner1.breakable { // detect one column
                    for (offset <- 0 until PeConfig().peNumEachColumn) {
                      if (blocks(base + offset)(i).nonEmpty) {
                        jumpStep1 = 0
                        out1.break()
                      }
                    }
                  }
                  // if can go out of the for-loop above,then it is an empty column, jumpStep + 1
                  jumpStep1 = jumpStep1 + 1
                }
              }
//              println("jumpStep1",jumpStep1)
            }
            // first add header for one small block in one big column
            if (blocks(base + offset)(col).nonEmpty) {
              val edge1 = ArrayBuffer[Byte](0.toByte, offset.toByte)
              val edge2 = ArrayBuffer[Byte](jumpStep1.toByte, 0.toByte)
              edgesArrayBuffer.append(edge1)
              edgesArrayBuffer.append(edge2)
            }
            // add all the edges in the small block in one big column
            while (blocks(base + offset)(col).nonEmpty) {
              val edge = blocks(base + offset)(col).dequeue()
              edgesArrayBuffer.append(edge)
            }
            // add extra padding zeros to make up for 128b
            if(blocks(base + offset)(col).isEmpty){
              var edgePaddingTo128Remainder = edgesArrayBuffer.length % 8
              if(edgePaddingTo128Remainder != 0){
                //padding to make a 128b packet
                for(i <- 0 until 8-edgePaddingTo128Remainder){
                  val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
                  edgesArrayBuffer.append(edge)
               }
              }

            }
          }
        }
      }

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

      println("dataArrayLen", dataArray.toArray.length)

      dataArray.toArray //return edgesArray

    }

  test("SboomTopTest"){


    compiled.doSim { dut =>
      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟
      //axi4 port1 for vex&edges
      val axiMemSimConfig1 = AxiMemorySimConfig(maxOutstandingReads = 2,maxOutstandingWrites = 8)
      val axiMemSimModel1 = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig1)
      // axi4 port2 for indices
//      val axiMemSimConfig2 = AxiMemorySimConfig(maxOutstandingReads = 2,maxOutstandingWrites = 8)
//      val axiMemSimModel2 = AxiMemorySim(compiled.dut.io.topAxiEdgeIndexPort, compiled.dut.clockDomain, axiMemSimConfig2)
//      axiMemSimModel1.start()
      edgeGen()
//      axiMemSimModel2.start()
//      axiMemSimModel1.memory.writeArray(0, vexGen())
//      val (edgeIndex, edge) = edgeGen()
    //     axiMemSimModel1.memory.writeArray(0x400000,edgeIndex)
//      axiMemSimModel2.memory.writeArray(0x400000,edgeIndex)
//      axiMemSimModel1.memory.writeArray(0x800000, edge)
      dut.clockDomain.waitSampling(count = 8)
      dut.clockDomain.waitSampling(count = 100)
//      val dataRead = axiMemSimModel1.memory.readArray(0, 10)
//      for (i <- 0 until 10) {
//        println(dataRead(i))
//      }
      dut.clockDomain.waitSampling(10000)
    }

  }
}







//import dispatcher.Dispatcher
//import org.scalatest.funsuite.AnyFunSuite
//import spinal.core._
//import spinal.core.sim.{SimConfig, _}
//import spinal.lib._
//import spinal.lib.bus.amba4.axi.sim.{AxiMemorySim, AxiMemorySimConfig}
//import dispatcher._
//
//import java.io.{File, FileOutputStream}
//import java.lang.Long.{parseLong, parseUnsignedLong}
//import scala.collection.mutable
//import scala.collection.mutable.ArrayBuffer
//import scala.io.Source
//import scala.math._
//import scala.util.Random
//import scala.util.control.Breaks
//
//class SboomTopTest extends AnyFunSuite {
//
//  object MySpinalConfig extends SpinalConfig(
//    defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC,resetActiveLevel = HIGH),
//    targetDirectory = "fpga/target",
//    oneFilePerComponent = false
//  )
//  val ipDir = "fpga/ip"
//  val xciSourcePaths = ArrayBuffer(
//    new File(ipDir).listFiles().map(ipDir + "/" + _.getName) :_*
//  )
//
//  val simConfig = SpinalSimConfig(_spinalConfig = MySpinalConfig)
//  //val xSimConfig = simConfig.copy(_workspacePath = "xSimWorkspace".withXSim.withXSimSourcesPaths(xciSource)Paths,ArrayBuffer(""))
//
//  val compiled= simConfig.withWave.withXilinxDevice("xczu7ev-ffvc1156-2-e").withXSim.compile(SboomTop())
////  val compiled= simConfig.withWave.compile(SboomTop())
//
//
////  val compiled= xSimConfig.withWave.compile(SboomTop())
////  val axiMemSimConfig = AxiMemorySimConfig()
////  val axiMemSimModel = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig)
//
//
//
//  def dataGen(): Array[Byte] = {
//    val data = new Array[Byte](100)
//
//    for (i <- 0 until 100) {
//      data(i) = i.toByte
//    }
//    data
//  }
//
//  def vexGen() = {
//
//    val random = new Random()
//    val vertexBuffer = ArrayBuffer[Byte]()
//    for (i <- 0 until 128 * 16) {
//
//      val num = 1.toByte //(random.nextInt(33) - 16).toByte
//      vertexBuffer.append(num)
//
//    }
//    vertexBuffer.toArray
//
//  }
//
//  //generate edges and corresponding indices
//  def edgeGen() = {
//    val filename = "./data/G34"
//    val firstLine = Source.fromFile(filename).getLines().next()
//    val firFields = firstLine.split(' ')
//    val width = scala.math.ceil(parseUnsignedLong(firFields(0)).toDouble / 64).toInt
//    val arrayWidth = width
////    println("arrayWidth = ",arrayWidth)
//    //put a queue inside each block
//    val blocks = Array.ofDim[mutable.Queue[ArrayBuffer[Byte]]](arrayWidth, arrayWidth)
//    for (row <- 0 until arrayWidth) {
//      for (col <- 0 until (arrayWidth)) {
//        blocks(row)(col) = mutable.Queue[ArrayBuffer[Byte]]()
//      }
//    }
//    //    val fileOutput = new FileOutputStream(new File("./dataSet/G1.bin"))
//    val remainLines = Source.fromFile(filename).getLines().drop(1)
//    for (line <- remainLines) {
//      val row = parseUnsignedLong(line.split(' ')(0)) - 1
//      val col = parseUnsignedLong(line.split(' ')(1)) - 1
//      val value = parseLong(line.split(' ')(2))
//      val edge = (row & ((1 << 6) - 1)) << 10 |
//        (col & ((1 << 6) - 1)) << 6 |
//        value & ((1 << 4) - 1)
//      val edgeBytes = ArrayBuffer[Byte](
//        edge.toByte,
//        (edge >>> 8).toByte
//      )
//
//      val block_row = (row / 64).toInt
//      val block_col = (col / 64).toInt
//      blocks(block_row)(block_col).enqueue(edgeBytes)
//      blocks(block_col)(block_row).enqueue(edgeBytes)
//    } //put all the edges into all queues
//
//    val edgesArrayBuffer = ArrayBuffer[ArrayBuffer[Byte]]()
//    val edgeIndexBuffer = ArrayBuffer[Byte]()
//    var edgeIndexByte:Int = 0
//    var flag = 0
//    var transfer_128 = 0
//    var block_empty = 0
//    var edgeCnt = 0
//    val goodIntervalBound = arrayWidth / PeConfig().peNumEachColumn * PeConfig().peNumEachColumn
//    val remainder = arrayWidth % (PeConfig().peNumEachColumn)
//
//    // To deal with all lines within good interval and all column blocks
//    var blockEmpty = 0
//    var bigLineBlockCnt = 0
//    var jumpStep = 0
//
//    for (base <- 0 until (goodIntervalBound) by PeConfig().peNumEachColumn) {
//      for (col <- 0 until arrayWidth) { //arrayWidth is the number of 64 * 64 blocks
//        do {
//          // 128bits conccatenation
////          edgeIndexByte = 0
//          flag = 0
//          for (offset <- 0 until PeConfig().peNumEachColumn) {
//            if (blocks(base + offset)(col).nonEmpty) {
//
//              blockEmpty = 0
//              flag = flag + 1
//              edgeCnt = edgeCnt + 1
//              if(edgeCnt >0 && edgeCnt%8 == 0){
//                transfer_128 = transfer_128 + 1
//              }
//
//              //generating edge indices
//              if(edgeCnt%2 == 1){
//                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte)<<4)
//              }
//              else{
//                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte))
//                edgeIndexBuffer.append(edgeIndexByte.toByte)
//                edgeIndexByte = 0
//              }
//              //put the edges into the buffer
//              val edge = blocks(base + offset)(col).dequeue()
//              edgesArrayBuffer.append(edge)
//            } else {
//              // Do nothing
////              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//              //               tempArray(offset) = edge
////              edgesArrayBuffer.append(edge)
//            }
//          } // 128bits conccatenation and paddings
//        } while (flag > 0) //  flag>0 means that there is no allZeros
//
//        if(flag == 0){
////        println("transfer_128 and edgeCnt",transfer_128,edgeCnt)
//          jumpStep = 0
//          bigLineBlockCnt = bigLineBlockCnt + 1
//          blockEmpty = blockEmpty + 1
//          if(blockEmpty == 1){
//            var edgePaddingTo128Remainder = edgeCnt % 8
//            var edgeIndexPaddingByteNum = (8 - edgePaddingTo128Remainder)/2
////            println("edgePaddingTo128Remainder",edgePaddingTo128Remainder)
//            if(edgePaddingTo128Remainder != 0){
//              //padding to make a 128b packet
//              for(i <- 0 until 8-edgePaddingTo128Remainder){
//                val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//                edgeCnt = edgeCnt + 1
//                edgesArrayBuffer.append(edge)
//              }
//              //edgeIndexPadding to make 32b index packet
//              for(i <- 0 until edgeIndexPaddingByteNum){
//                edgeIndexBuffer.append(0.toByte)
//              }
//              transfer_128 = transfer_128 + 1
//            }
//
//            //forced to add seperator with 128b all zeros
//            for(i <- 0 until 8){
//              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//              edgeCnt = edgeCnt + 1
//              edgesArrayBuffer.append(edge)
//            }
////            //edgeIndexPadding to make 32b index packet
////            for(i <- 0 until 4){
////              edgeIndexBuffer.append(0.toByte)
////            }
//            //break implementatioin in scala
//            val out = new Breaks
//            val inner = new Breaks
//            out.breakable {
//              for (i <- col+1 until arrayWidth) {
//                inner.breakable { // detect one column
//                  for (offset <- 0 until PeConfig().peNumEachColumn) {
//                    if (blocks(base + offset)(i).nonEmpty) {
//                      jumpStep = 0
//                      out.break()
//                    }
//                  }
//                }
//                // if can go out of the for-loop above,then it is an empty column, jumpStep + 1
//                jumpStep = jumpStep + 1
//              }
//            }
//            println("jumpStep",jumpStep)
//            //edgeIndexPadding to make 32b index packet,real padding index
//            if (jumpStep>0){
//              edgeIndexBuffer.append(((jumpStep >> 24) | 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep >> 16) & 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep >> 8) & 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep) & 0xFF).toByte)
//
//            }else {
//              for(i <- 0 until 4){
//                edgeIndexBuffer.append(0.toByte)
//              }
//            }
//            transfer_128 = transfer_128 + 1
////          println("debug transfer_128",transfer_128)
//
//            //judge the number of 128b transfers after the force-added 128'b0 seperator
//            if(transfer_128 % 4 == 1){
//
//              val edgePadding =  ArrayBuffer.fill(16 * 3)(0.toByte)
//              transfer_128 = transfer_128 + 3
//              edgesArrayBuffer.append(edgePadding)
//              //edgeIndex Padding
//              for(i <- 0 until 3*4){ //for each 128b edge packet, there are 4Bytes needed for index
//                edgeIndexBuffer.append(0.toByte)
//              }
//            } else if(transfer_128 % 4 == 2){
//              val edgePadding = ArrayBuffer.fill(16 * 2)(0.toByte)
//              val indexPadding = ArrayBuffer.fill(2)(0.toByte)
//              transfer_128 = transfer_128 + 2
//              edgesArrayBuffer.append(edgePadding)
//              //edgeIndex Padding
//              for(i <- 0 until 2*4){
//                edgeIndexBuffer.append(0.toByte)
//              }
//            } else if (transfer_128 % 4 == 3) {
//              val edgePadding = ArrayBuffer.fill(16 * 1)(0.toByte)
//              transfer_128 = transfer_128 + 1
//              edgesArrayBuffer.append(edgePadding)
//              //edgeIndex Padding
//              for(i <- 0 until 1*4){
//                edgeIndexBuffer.append(0.toByte)
//              }
//            }
//          }
//
//// bigLine End flag with additional 512'b0(equivalent to add another 512'b0 seperator after the first seperator)
//          if(bigLineBlockCnt%32 == 0){
//            val edgePadding =  ArrayBuffer.fill(16 * 4)(0.toByte)
//            transfer_128 = transfer_128 + 4
//            edgesArrayBuffer.append(edgePadding)
//          }
//        }
//      }
//    }
//
//    //To deal with remaining blocks
//    var jumpStep1 = 0
//    if (remainder > 0) {
//      for (col <- 0 until arrayWidth) {
//        do {
//          flag = 0
//          for (offset <- 0 until remainder) {
//            if (blocks(goodIntervalBound + offset)(col).nonEmpty) {
//              println("queue nonempty in remaindar",goodIntervalBound,col)
//              blockEmpty = 0
//              flag = flag + 1
//
//              val edge = blocks(goodIntervalBound+offset)(col).dequeue()
//              edgesArrayBuffer.append(edge)
//              edgeCnt = edgeCnt + 1
//              if(edgeCnt>0 && edgeCnt%8 == 0){
//                transfer_128 = transfer_128 + 1
//              }
//
//              //generating edge indices
//              if(edgeCnt%2 == 1){
//                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte)<<4)
//              }
//              else{
//                edgeIndexByte = (edgeIndexByte | ((offset+1).toByte))
//                edgeIndexBuffer.append(edgeIndexByte.toByte)
//                edgeIndexByte = 0
//              }
//
//            } else {
//              //Do Nothing
////              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
////              //tempArray(offset) = edge
////              edgesArrayBuffer.append(edge)
//            }
//          }
//          // if there is no enough blocks, we need to add the extra blocks and edges to be the multiples of number of PEs
////          for (offset <- remainder until PeConfig().peNumEachColumn) {
////            val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
////            edgesArrayBuffer.append(edge)
////          }
//        } while (flag > 0)
//
//        if (flag == 0) {
//          jumpStep1 = 0
//          blockEmpty = blockEmpty + 1
//          if(blockEmpty == 1){
//            //          println("-------transfer_128----------",transfer_128)
//            var edgePaddingTo128Remainder = edgeCnt % 8
//            var edgeIndexPaddingByteNum = (8 - edgePaddingTo128Remainder)/2
//
//            if(edgePaddingTo128Remainder != 0){
//              //padding to make a 128b packet
//              for(i <- 0 until 8-edgePaddingTo128Remainder){
//                val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//                edgesArrayBuffer.append(edge)
//              }
//              //edgeIndexPadding to make 32b index packet
//              for(i <- 0 until edgeIndexPaddingByteNum){
//                edgeIndexBuffer.append(0.toByte)
//              }
//              transfer_128 = transfer_128 + 1
//            }
//
//            // forced to add extra seperator with 128bit all zeros
//            for(i <- 0 until 8){
//              val edge = ArrayBuffer.fill(DispatcherConfig().edgeByteLen)(0.toByte)
//              edgeCnt = edgeCnt + 1
//              edgesArrayBuffer.append(edge)
//            }
//
//
//            //edgeIndexPadding to make 32b index packet
////            for(i <- 0 until 4){
////              edgeIndexBuffer.append(0.toByte)
////            }
//            //break implementatioin in scala
//            val out1 = new Breaks
//            val inner1 = new Breaks
//            out1.breakable {
//              for (i <- col+1 until arrayWidth) {
//                inner1.breakable { // detect one column
//                  for (offset <- 0 until PeConfig().peNumEachColumn) {
//                    if (blocks(goodIntervalBound + offset)(i).nonEmpty) {
//                      jumpStep1 = 0
//                      out1.break()
//                    }
//                  }
//                }
//                // if can go out of the for-loop above,then it is an empty column, jumpStep + 1
//                jumpStep1 = jumpStep1 + 1
//              }
//            }
////          println("jumpStep",jumpStep)
//            //edgeIndexPadding to make 32b index packet,real padding index
//            if (jumpStep1>0){
//              edgeIndexBuffer.append(((jumpStep1 >> 24) | 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep1 >> 16) & 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep1 >> 8) & 0xFF).toByte)
//              edgeIndexBuffer.append(((jumpStep1) & 0xFF).toByte)
//            }else {
//              for(i <- 0 until 4){
//                edgeIndexBuffer.append(0.toByte)
//              }
//            }
//            transfer_128 = transfer_128 + 1
//
//            if (transfer_128 % 4 == 1) {
//              val padding = ArrayBuffer.fill(16 * 3)(0.toByte)
//              transfer_128 = transfer_128 + 3
//              edgesArrayBuffer.append(padding)
//            } else if (transfer_128 % 4 == 2) {
//              val padding = ArrayBuffer.fill(16 * 2)(0.toByte)
//              transfer_128 = transfer_128 + 2
//              edgesArrayBuffer.append(padding)
//            } else if (transfer_128 % 4 == 3) {
//              val padding = ArrayBuffer.fill(16 * 1)(0.toByte)
//              transfer_128 = transfer_128 + 1
//              edgesArrayBuffer.append(padding)
//            }
//          }
//
//          // bigLine End flag with additional 512'b0(equivalent to add another 512'b0 seperator after the first seperator)
//          if(bigLineBlockCnt%32 == 0){
//            val edgePadding =  ArrayBuffer.fill(16 * 4)(0.toByte)
//            transfer_128 = transfer_128 + 4
//            edgesArrayBuffer.append(edgePadding)
//          }
//        }
//      }
//    }
//
//    //    println("edge_number", edgesArrayBuffer.mkString(","))
//    //*************************************generate edge bin file ****************************//
//    val dataArray = ArrayBuffer[Byte]()
//    for (i <- edgesArrayBuffer.indices) {
//      val innerArr = edgesArrayBuffer(i)
//      for (j <- innerArr.indices) {
//        dataArray.append(innerArr(j))
//      }
//    }
//
//    import java.io._
//    val fos = new FileOutputStream("edge.bin")
//    val dos = new DataOutputStream(fos)
//    for (d <- dataArray) {
//      dos.write(d.toByte)
//    }
//    dos.close()
//
//    //************************************generate edgeIndex bin file*************************************//
////    val edgeIndexArray = ArrayBuffer[Byte]()
////    for (i <- edgeIndexBuffer.indices) {
////      val innerArr = edgesArrayBuffer(i)
////      for (j <- innerArr.indices) {
////        dataArray.append(innerArr(j))
////      }
////    }
////
//    val fos1 = new FileOutputStream("edgeIndex.bin")
//    val dos1 = new DataOutputStream(fos1)
//    for (d <- edgeIndexBuffer) {
//      dos1.write(d.toByte)
//    }
//    dos1.close()
//
//    println("dataArrayLen", dataArray.toArray.length)
////    print(edgeIndexBuffer.toArray.toString)
////    print(dataArray)
////    println("transfer_128",transfer_128)
//    (edgeIndexBuffer.toArray,dataArray.toArray)
//  }
//
//  test("SboomTopTest"){
//
//    compiled.doSim { dut =>
//
//      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟
//
//      //axi4 port1 for vex&edges
//      val axiMemSimConfig1 = AxiMemorySimConfig(maxOutstandingReads = 2,maxOutstandingWrites = 8)
//      val axiMemSimModel1 = AxiMemorySim(compiled.dut.io.topAxiMemControlPort, compiled.dut.clockDomain, axiMemSimConfig1)
//
//      // axi4 port2 for indices
//      val axiMemSimConfig2 = AxiMemorySimConfig(maxOutstandingReads = 2,maxOutstandingWrites = 8)
//      val axiMemSimModel2 = AxiMemorySim(compiled.dut.io.topAxiEdgeIndexPort, compiled.dut.clockDomain, axiMemSimConfig2)
//
//      axiMemSimModel1.start()
//      axiMemSimModel2.start()
//
//      axiMemSimModel1.memory.writeArray(0, vexGen())
//      val (edgeIndex, edge) = edgeGen()
////      axiMemSimModel1.memory.writeArray(0x400000,edgeIndex)
//      axiMemSimModel2.memory.writeArray(0x400000,edgeIndex)
//      axiMemSimModel1.memory.writeArray(0x800000, edge)
//
//      dut.clockDomain.waitSampling(count = 8)
//      dut.clockDomain.waitSampling(count = 100)
//
//      val dataRead = axiMemSimModel1.memory.readArray(0, 10)
//      for (i <- 0 until 10) {
//        println(dataRead(i))
//      }
//      dut.clockDomain.waitSampling(10000)
//
//    }
//  }
//
//}


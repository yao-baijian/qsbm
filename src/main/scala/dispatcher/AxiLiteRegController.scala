package dispatcher

package dispatcher

import spinal.core.Component
import spinal.core.{B, _}
import spinal.lib._
import spinal.lib.bus.amba4.axilite._
import spinal.lib.tools.DataAnalyzer

case class SbmConfigPort() extends Bundle with IMasterSlave{

  val startConfigRegOut = UInt(32 bits)

  val rowsInOneIterRegOut = UInt(32 bits)
  val blocksInOneRowRegOut = UInt(32 bits)
  val alphaNumber = UInt(32 bits)
  val alphaChangeVal = UInt(32 bits)
  val xi = UInt(32 bits)
  val dt = UInt(32 bits)

  override def asMaster(): Unit = {

    out(startConfigRegOut)
    out(rowsInOneIterRegOut)
    out(blocksInOneRowRegOut)
    out(alphaNumber)
    out(alphaChangeVal)
    out(xi)
    out(dt)

  }
}

case class AxiLiteRegController() extends Component {

  val axiLiteConfig = AxiLite4Config(addressWidth = 32, dataWidth = 32)

  val io = new Bundle{

    val axiLiteSlave =  slave(AxiLite4(axiLiteConfig))
    val sbmConfigPort = master(SbmConfigPort())

    val signalsFromDisp = in Bits(4 bits)
    val signalsFromAxiMemCtrl = in Bits(4 bits)

  }

  val axiLiteCtrl = AxiLite4SlaveFactory(io.axiLiteSlave)

  //axilite memory mapped Regs inside//
  val startFlagReg = axiLiteCtrl.driveAndRead(io.sbmConfigPort.startConfigRegOut,address = (2*1024*1024*1024 + 0)& 0xffffffffL) init 0 //driveAndRead()函数返回的就是一个Reg
  //  val completeFlagReg = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 4)& 0xffffffffL) init 0
  //
  //  val rowsInOneIter = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 8)& 0xffffffffL) init 0
  //  val blocksInOneRow = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 12)& 0xffffffffL) init 0
  //  val alphaNumber = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 16)& 0xffffffffL) init 0
  //  val alphaChangeVal = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 20)& 0xffffffffL) init 0
  //  val xi = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 24)& 0xffffffffL) init 0
  //  val dt = axiLiteCtrl.createReadAndWrite(UInt(32 bits),address = (2*1024*1024*1024 + 28)& 0xffffffffL) init 0

  //  val completeFlagReg = axiLiteCtrl.driveAndRead(io.sbmConfigPort.startConfigRegOut,address = 16*1024*1024 + 4) init 0

  //  when(io.signalsFromDisp === B"4'b0001"){
  //
  ////    startFlagReg := 0
  //
  //  }
  //
  //  when(io.signalsFromAxiMemCtrl === B"4'b0001"){
  ////    completeFlagReg := 1
  //  }


  noIoPrefix()

  //  io.sbmConfigPort.startConfigRegOut :=  startFlagReg

  //  io.sbmConfigPort.rowsInOneIterRegOut := rowsInOneIter
  //  io.sbmConfigPort.blocksInOneRowRegOut := blocksInOneRow
  //  io.sbmConfigPort.alphaNumber := alphaNumber
  //  io.sbmConfigPort.alphaChangeVal := alphaChangeVal
  //  io.sbmConfigPort.xi := xi
  //  io.sbmConfigPort.dt := dt
}

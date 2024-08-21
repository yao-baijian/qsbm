package dispatcher

import spinal.core._
import spinal.core.sim._
import spinal.lib._
case class ScheduleBoardWrPort() extends Bundle{
  //write cmd
  val wen = Bool()
  val addr = UInt(2 bits)

  //write data
  val busy = Bool()
  val colNum = UInt(4 bits)
}

case class ScheduleBoardRdPort() extends Bundle with IMasterSlave {
  val ren = Bool()
  val addr = UInt(2 bits)

  val busy = Bool()
  val colNum = UInt(4 bits)

  override def asMaster(): Unit = {
    out(ren, addr)
    in(busy,colNum)
  }
}

case class ScheduleBoard() extends Component {
  val io = new Bundle{
    val scheduleBoardWrPorts = Vec(slave Stream(ScheduleBoardWrPort()),4)
    val scheduleBoardRdPorts = Vec(slave(ScheduleBoardRdPort()),4)
  }

  val entry = new Bundle{
    val busy = Reg(Bool()) init False
    val colNum = Reg(UInt(4 bits)) init 0
  }

  val scheduleBoardMem = Vec(entry,4)

  val streamWrPortsArbiter = StreamArbiterFactory.roundRobin.onArgs(io.scheduleBoardWrPorts(0),io.scheduleBoardWrPorts(1),io.scheduleBoardWrPorts(2),io.scheduleBoardWrPorts(3))
  streamWrPortsArbiter.ready := True

  //write port connection
  when(streamWrPortsArbiter.valid){
    scheduleBoardMem(streamWrPortsArbiter.payload.addr).busy := streamWrPortsArbiter.payload.busy
    scheduleBoardMem(streamWrPortsArbiter.payload.addr).colNum := streamWrPortsArbiter.payload.colNum
  }

  //read port connection
  for(i <- 0 to 3){
    when(io.scheduleBoardRdPorts(i).ren) {
      io.scheduleBoardRdPorts(i).busy := scheduleBoardMem(io.scheduleBoardRdPorts(i).addr).busy
      io.scheduleBoardRdPorts(i).colNum := scheduleBoardMem(io.scheduleBoardRdPorts(i).addr).colNum
    }.otherwise{
      io.scheduleBoardRdPorts(i).busy := False
      io.scheduleBoardRdPorts(i).colNum := 0
    }
  }
}

object Test extends App{

  val compiled = SimConfig.withWave.compile(ScheduleBoard())
  compiled.doSim{dut=>
    dut.clockDomain.forkStimulus(period = 2)
    dut.io.scheduleBoardRdPorts
    //    dut.writeField(0,"busy",1)
    //    dut.writeField(1,"colNum",1)

    //    var data0 = dut.readField(1,"busy").toInt
    //    var data1 = dut.readField(3,"busy").toInt
    //    println(data1)
    //    dut.clockDomain.waitSampling()      //目前感觉要两个都写了才能产生波形时钟

    //    sleep(10)
    //    dut.io.data1 #= 32
    //    dut.io.data2 #= 32
    sleep(100)
    //    println(s"${dut.io.sum.toInt}")

  }
}

package dispatcher

import spinal.core.sim._
import spinal.sim._
import spinal.lib._
import org.scalatest.funsuite.AnyFunSuite
import spinal.lib.sim.StreamDriver
class ScheduleBoardTest extends AnyFunSuite {


  val compiled= SimConfig.withWave.compile(ScheduleBoard())

  test("ScheduleBoardTest"){

    compiled.doSim { dut =>
      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟
      dut.clockDomain.waitSampling(count = 10)
      // drive random data and add pushed data to scoreboard

      val (streamDriver0, cmdQueue0) = StreamDriver.queue(dut.io.scheduleBoardWrPorts(0), dut.clockDomain)
      cmdQueue0.enqueue{payload =>payload.randomize()}
      cmdQueue0.enqueue{payload =>payload.randomize()}
      cmdQueue0.enqueue{payload =>payload.randomize()}

      val (streamDriver1, cmdQueue1) = StreamDriver.queue(dut.io.scheduleBoardWrPorts(1), dut.clockDomain)
      cmdQueue1.enqueue { payload => payload.randomize() }
      cmdQueue1.enqueue { payload => payload.randomize() }
      cmdQueue1.enqueue { payload => payload.randomize() }

      val (streamDriver2, cmdQueue2) = StreamDriver.queue(dut.io.scheduleBoardWrPorts(2), dut.clockDomain)
      cmdQueue2.enqueue { payload => payload.randomize() }
      cmdQueue2.enqueue { payload => payload.randomize() }
      cmdQueue2.enqueue { payload => payload.randomize() }

      val (streamDriver3, cmdQueue3) = StreamDriver.queue(dut.io.scheduleBoardWrPorts(3), dut.clockDomain)
      cmdQueue3.enqueue { payload => payload.randomize() }
      cmdQueue3.enqueue { payload => payload.randomize() }
      cmdQueue3.enqueue { payload => payload.randomize() }



//      StreamDriver(dut.io.scheduleBoardWrPorts(0), dut.clockDomain) { payload =>
////        dut.io.scheduleBoardWrPorts(0).valid #= true
//        payload.randomize()
//        true
//      }

//      StreamDriver(dut.io.scheduleBoardWrPorts(1), dut.clockDomain) { payload =>
////        dut.io.scheduleBoardWrPorts(1).valid #= true
//        payload.randomize()
//        true
//      }

//      StreamDriver(dut.io.scheduleBoardWrPorts(2), dut.clockDomain) { payload =>
//        dut.io.scheduleBoardWrPorts(2).valid #= false
////        payload.randomize()
//        true
//      }

//      StreamDriver(dut.io.scheduleBoardWrPorts(3), dut.clockDomain) { payload =>
//        dut.io.scheduleBoardWrPorts(3).valid #= false
////        payload.randomize()
//        true
//      }

      dut.clockDomain.waitSampling(count = 100)
    }
  }
}

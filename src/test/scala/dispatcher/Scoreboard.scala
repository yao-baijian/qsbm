package dispatcher

import PE.PETopConfig
import PE.Config
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.ClockDomain.FixedFrequency
import spinal.core.{ClockDomainConfig, IntToBuilder, SYNC, SpinalConfig}
import spinal.core.sim._
import spinal.sim._
import spinal.lib._

class TopTb extends AnyFunSuite {

    val pe_top_config = PETopConfig()
    val compiled= SimConfig
      .withWave
      .compile(PE.PeTop(pe_top_config))

    test("hello"){

        compiled.doSim { dut =>
            dut.clockDomain.forkStimulus(100)
            dut.clockDomain.waitSampling() 
            dut.clockDomain.waitSampling(10)
            dut.clockDomain.waitSampling(10)
        }
    }


}


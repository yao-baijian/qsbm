package dispatcher

import org.scalatest.funsuite.AnyFunSuite
import spinal.core.ClockDomain.FixedFrequency
import spinal.core.{ClockDomainConfig, IntToBuilder, SYNC, SpinalConfig}
import spinal.core.sim._
import spinal.sim._
import spinal.lib._
class AxiMemCtrlTest extends AnyFunSuite {
  val pe_top_config = PE.PETopConfig()
  val compiled= SimConfig
    .withWave
//    .withIVerilog
    .compile(PE.PeTop(pe_top_config))

  test("hello"){
//    implicit val _ = "."

    compiled.doSim { dut =>
//
      dut.clockDomain.forkStimulus(100) //产生周期为10个单位的时钟
      dut.clockDomain.waitSampling() //等一个时钟上升沿到来
//      dut.io.a #= 10L //
//      dut.io.b #= 20L
      dut.clockDomain.waitSampling(10)
//      assert(200 == dut.io.prod.toLong ,"乘法模块出错！")
      dut.clockDomain.waitSampling(10)

//      println(dut.io.prod.toLong)

//    println("hello")
    }
  }


}

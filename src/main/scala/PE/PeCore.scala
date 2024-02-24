/*
 * @Author: Yao Baijian eebyao@ust.hk
 * @Date: 2023-11-17 16:39:05
 * @LastEditors:  
 * @LastEditTime: 2023-11-30 10:09:42
 * @FilePath: \sboom\src\main\scala\PE\PeCore.scala
 * @Description: 
 * 
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved. 
 */

package PE

import org.scalatest.Assertions.convertToEqualizer
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

case class PeCore(config: PeConfig) extends Component {

    val io_state = new Bundle {
        val last_update     = in Bool()
        val globalreg_done  = in Bool()
        val switch_done     = in Bool()
        val all_zero        = in Bool()
        val pe_busy         = out Bool()
        val need_new_vertex = out Bool()
    }
  
    val io_edge = new Bundle {
        val edge_value  = Vec(in Bits(config.data_width bits), config.thread_num)
        val edge_valid  = Vec(in Bool(), config.thread_num)
        val edge_ready  = Vec(out Bool(), config.thread_num)
        val tag_value   = Vec(in Bits(config.tag_width bits), config.thread_num)
        val tag_valid   = Vec(in Bool(), config.thread_num)
        val tag_ready   = Vec(out Bool(), config.thread_num)
    }

    val io_vertex = new Bundle {
        val addr        = Vec(out UInt (config.addr_width bits),config.thread_num)
        val data        = Vec(in Bits  (config.data_width bits),config.thread_num)
    }

    val io_update = new Bundle {
        val wr_valid    = Vec(out Bool(),config.thread_num)
        val wr_addr     = Vec(out UInt (config.addr_width bits),config.thread_num)
        val wr_data     = Vec(out Bits (config.data_width bits),config.thread_num)
        val rd_addr     = Vec(out UInt (config.addr_width bits),config.thread_num)
        val rd_data     = Vec(in  Bits (config.data_width bits),config.thread_num)
    }

    //-----------------------------------------------------------
    // Value Declaration
    // Frontend
    //-----------------------------------------------------------
    val f0_valid            = Vec(Bool(), config.thread_num )
    val real_addr           = Vec(Bits(3 + 6 bits), config.thread_num)
    val intrahaz_vec        = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_val        = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_f0         = Vec(Bool(), config.thread_num)
    val intrahaz_poz_s0_f0  = Vec(UInt(3 bits), config.thread_num)
    val intrahaz_poz_add_p1 = Vec(UInt(3 bits), 4)
    val intrahaz_poz_add_p2 = Vec(UInt(3 bits), 1)

    val f1_valid            = Vec(Reg(Bool()) init False, config.thread_num )
    val real_addr_f1        = Vec(Reg(Bits(3 + 6 bits)) init 0, config.thread_num)
    val interhaz_vec_f1     = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_poz_f1     = Vec(Bool(), config.thread_num )
    // TODO invalidate redundunt address here
    val real_addr_valid_f1  = Vec(Reg(Bool()) init False, config.thread_num )
    val edge_value_f1       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val update_ram_addr_f1  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val vertex_reg_addr_f1  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)

    val interhaz_adder_vec  = Vec(Bits (3 bits), config.extra_adder_num)

    //-----------------------------------------------------------
    // Pipeline
    //-----------------------------------------------------------
    val real_addr_h1        = Vec(Reg(Bits(3 + 6 bits)) init 0, config.thread_num)
    val real_addr_valid_h1  = Vec(Reg(Bool()) init False, config.thread_num )
    val vertex_reg_data_h1  = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val edge_value_h1       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val update_ram_addr_h1  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val h1_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val multiply_data_h1    = Vec(SInt(config.data_width bits), config.thread_num)

    val h2_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val multiply_data_h2    = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val entry_valid         = Vec(Reg(Bool()) init False, config.thread_num)
//  TODO expand intrahaz_index to multi bits
    val intrahaz_index      = Vec(Reg(Bool()) init False, config.thread_num)
    val interhaz_valid_h2   = Vec(Reg(Bool()) init False, config.thread_num)
    val interhaz_index      = Vec(Reg(Bits(3 bits)) init 0, config.thread_num)
    // 8 + 4 calculate result
    val updata_data_old_h2  = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val normal_haz_data_h2  = Vec(SInt(config.data_width bits), config.thread_num)
    val normal_update_data_h2= Vec(SInt(config.data_width bits), config.thread_num)
    val extra_haz_data_h2   = Vec(SInt(config.data_width bits), 4)
    val extra_update_data_h2= Vec(SInt(config.data_width bits), 4)

    val update_ram_addr_h2  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val updata_data_h2      = Vec(SInt(config.data_width bits), 8)

    val adder_en_1          = Vec(Bool(), 4)
    val adder_en            = Vec(Bits(4 bits), 8)
    val intrahaz_table      = Vec(Vec(Bool(), 8), 8)
    val adder_h2_s1         = Vec(Vec(SInt(config.data_width bits), config.thread_num), 4)
    val adder_h2_s4         = Vec(SInt(config.data_width bits), 4)

    val h3_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val update_data_h3      = Vec(SInt(config.data_width bits), config.thread_num)
    val ram_data_h3         = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val update_ram_addr_h3  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)

    //-----------------------------------------------------------
    // Misc
    //-----------------------------------------------------------

    val pe_busy             = Reg(Bool()) init False
    val need_new_vertex     = Reg(Bool()) init False
    val fifo_rdy            = Vec(Reg(Bool()) init True, config.thread_num)

    //-----------------------------------------------------------
    // Wiring
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        io_edge.edge_ready(i)       := fifo_rdy(i)
        io_state.pe_busy            := pe_busy
        io_state.need_new_vertex    := need_new_vertex
    }

    val pe_fsm = new StateMachine {
        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val WAIT_DONE = new State
        val PAUSE = new State

        IDLE
          .whenIsActive {
              when (io_state.globalreg_done) {
                  need_new_vertex  := False
              }
              when (io_state.last_update) {
                  for (i <- 0 until config.thread_num) {
                      fifo_rdy(i) := False
                  }
                  goto(PAUSE)
              } elsewhen (!need_new_vertex && io_edge.edge_valid.orR === True) {
                  pe_busy := True
                  for (i <- 0 until config.thread_num) {
                      fifo_rdy(i) := True
                  }
                  goto(OPERATE)
              }
          }
        OPERATE
          .whenIsActive {
              when(io_state.all_zero) {
                  goto(WAIT_DONE)
              }
          }
        WAIT_DONE
          .whenIsActive {
              when(h3_valid.orR === False) {
                  when (io_state.last_update) {
                      for (i <- 0 until config.thread_num) {
                          fifo_rdy(i) := False
                      }
                      goto(PAUSE)
                  } otherwise{
                      pe_busy := False
                      need_new_vertex  := True
                      goto(IDLE)
                  }
              }
          }
        PAUSE
          .whenIsActive {
              when(io_state.switch_done) {
                  pe_busy := False
                  for (i <- 0 until config.thread_num) {
                      fifo_rdy(i) := True
                  }
                  need_new_vertex  := True
                  goto(IDLE)
              }
          }
    }

    //-----------------------------------------------------------
    // Frontend
    //-----------------------------------------------------------
    // TODO add valid signal for address comparing
    // pipeline the frontend logic
    //-----------------------------------------------------------
    // f0  find intra stage hazard
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num - 1) {
        real_addr(i) := io_edge.tag_value(i) ## io_edge.edge_value(i)(9 downto 4)
        for (j <- i + 1 until config.thread_num) {
            intrahaz_vec(i)(j) := (real_addr(i) === real_addr(j) && f0_valid(i) && f0_valid(j)) ? True | False
        }
        for (k <- 0 until i) {
            intrahaz_val(i)(k) := intrahaz_vec(k)(i)
        }
        intrahaz_f0(i) := intrahaz_vec(i).orR && !intrahaz_val(i).orR

        for (l <- 0 until 4) {
            intrahaz_poz_add_p1(l) := intrahaz_f0(l*2).asUInt + intrahaz_f0(l*2+1).asUInt
        }

        for (m <- 0 until 1) {
            intrahaz_poz_add_p2(m) := intrahaz_poz_add_p1(m*2) + intrahaz_poz_add_p1(m * 2 + 1)
        }
    }

    // TODO has some redundunt logic, need to remove them
    intrahaz_poz_s0_f0(0)   := intrahaz_f0(0).asUInt
    intrahaz_poz_s0_f0(1)   := intrahaz_poz_add_p1(0)
    intrahaz_poz_s0_f0(2)   := intrahaz_poz_add_p1(0) + intrahaz_f0(2).asUInt
    intrahaz_poz_s0_f0(3)   := intrahaz_poz_add_p2(0)
    intrahaz_poz_s0_f0(4)   := intrahaz_poz_add_p2(0) + intrahaz_f0(4).asUInt
    intrahaz_poz_s0_f0(5)   := intrahaz_poz_add_p2(0) + intrahaz_poz_add_p1(2)
    intrahaz_poz_s0_f0(6)   := intrahaz_poz_add_p2(0) + intrahaz_poz_add_p1(2) + intrahaz_f0(6).asUInt

    //-----------------------------------------------------------
    // pipeline f1: find inter stage hazard WRITE AFTER READ
    //-----------------------------------------------------------
    //  TODO change valid_f0(i) to real valid criteria
    //  TODO when (io_edge_fifo.edge_fifo_ready && io_edge_fifo.edge_fifo_valid && io_edge_fifo.edge_fifo_in =/= 0)

    for (i <- 0 until config.thread_num) {
        when(f0_valid(i)) {
            f1_valid(i)             := True
            real_addr_f1(i)         := real_addr(i)
            vertex_reg_addr_f1(i)   := io_edge.edge_value(i)(15 downto 10).asUInt
            update_ram_addr_f1(i)   := io_edge.edge_value(i)(9 downto 4).asUInt
            edge_value_f1(i)        := io_edge.edge_value(i)(3 downto 0).asSInt
    // TODO pass hazard table
        } otherwise {
            vertex_reg_addr_f1(i)   := 0
            update_ram_addr_f1(i)   := 0
            edge_value_f1(i)        := 0
            f1_valid(i)             := False
        }
    }

    for (i <- 0 until config.thread_num) {
        for (j <- 0 until config.thread_num) {
            interhaz_vec_f1 (i)(j) := ((real_addr_f1(i) === real_addr_h1(j))&&
              real_addr_valid_f1(i) && real_addr_valid_h1(j)) ? True | False
        }
    }

    for (i <- 0 until config.thread_num) {
        io_vertex.addr(i)      := vertex_reg_addr_f1(i)
    }

    //-----------------------------------------------------------
    // pipeline h1: Multiplication
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        when(f1_valid(i)) {
            edge_value_h1(i) := edge_value_f1(i)
            update_ram_addr_h1(i) := update_ram_addr_f1(i)
            //            updata_data_old_h2      := io_update_ram.rd_data.asSInt
            vertex_reg_data_h1(i) := io_vertex.data(i).asSInt
            //            hazard_s1_h2            := hazard_s1_h1
            h1_valid(i) := True
        } otherwise {
            edge_value_h1(i) := 0
            update_ram_addr_h1(i) := 0
            vertex_reg_data_h1(i) := 0
            h1_valid(i) := True
        }
    }

    for (i <- 0 until config.thread_num) {
        multiply_data_h1(i) := vertex_reg_data_h1(i) * edge_value_h1(i)
    }
    //    io_update_ram.rd_addr   := update_ram_addr_h1


    //-----------------------------------------------------------
    // pipeline h2
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(h1_valid(i)) {
            h2_valid(i)         := True
            interhaz_valid_h2(i):=
            multiply_data_h2(i) := multiply_data_h1(i)
            updata_data_old_h2(i) :=
        } otherwise {
            //            edge_value_h2 := 0
            //            update_ram_addr_h2 := 0
            //            updata_data_old_h2 := 0
            //            vertex_reg_data_h2 := 0
            //            hazard_s1_h2 := False
            h2_valid(i) := False
        }
    }

    //  TODO fix bit width here
    for (i <- 0 until config.thread_num) {
        normal_haz_data_h2(i) := (interhaz_valid_h2(i) ? update_data_h3(interhaz_index(i).asUInt) | updata_data_old_h2(i))
        normal_update_data_h2(i) := (entry_valid(i) && !intrahaz_index(i)) ? (normal_haz_data_h2(i) + multiply_data_h2(i))(config.data_width - 1 downto 0) | 0
    }

    for (i <- 0 until config.thread_num) {
        extra_haz_data_h2(i) := (interhaz_valid_h2(i) ? update_data_h3(interhaz_index(i).asUInt) | updata_data_old_h2(i))(config.data_width - 1 downto 0)
    }

    // TODO it is possible to reduce adder number into 2 or 1
    // Pass intar hazard date into extra adder matrix
    for (i <- 0 until config.thread_num) {
        for (j <- 0 until config.thread_num) {
            adder_h2_s1(adder_en(i)(2 downto 0).asUInt)(j) := (adder_en(i)(3) && intrahaz_table(adder_en(i)(2 downto 0).asUInt)(j)) ? multiply_data_h2(j) | 0
        }
    }

    // TODO add extra_haz_data_h2 here and sum them up to updata_data_h2
    // Use balanced adder to add up intra hazard data
    for (i <- 0 until 4) {
        adder_h2_s4(i) := adder_en_1(i) ? adder_h2_s1(i).reduceBalancedTree(_ + _) | 0
    }

    //    updata_data_h2 := (hazard_s1_h2 ? ram_data_h3 | updata_data_old_h2  + vertex_reg_data_h2 * edge_value_h2) (config.data_width - 1 downto 0);

    //-----------------------------------------------------------
    // pipeline h3 write back
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(h2_valid(i)) {
            update_ram_addr_h3(i)   := update_ram_addr_h2(i)
            ram_data_h3(i)          := updata_data_h2(i)
            h3_valid(i)             := True
        } otherwise {
            update_ram_addr_h3(i)   := 0
            ram_data_h3(i)          := 0
            h3_valid(i)             := False
        }

        io_update.wr_valid(i) := h3_valid(i)
        io_update.wr_addr(i) := update_ram_addr_h3(i)
        io_update.wr_data(i) := ram_data_h3(i).asBits
    }
}
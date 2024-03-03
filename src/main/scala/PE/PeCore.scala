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
        val tag_value   = Vec(in Bits(config.tag_width - 1 bits), config.thread_num)
        val edge_valid  = Vec(in Bool(), config.thread_num)
        val edge_ready  = Vec(out Bool(), config.thread_num)
    }

    val io_vertex = new Bundle {
        val addr        = Vec(out UInt (config.addr_width bits),config.thread_num)
        val data        = Vec(in Bits  (config.data_width bits),config.thread_num)
    }

    val io_update = new Bundle {
        val wr_valid    = Vec(out Bool(),config.thread_num)
        val wr_addr     = Vec(out UInt (config.extend_addr_width bits),config.thread_num)
        val wr_data     = Vec(out Bits (config.data_width bits),config.thread_num)
        val rd_addr     = Vec(out UInt (config.extend_addr_width bits),config.thread_num)
        val rd_data     = Vec(in  Bits (config.data_width bits),config.thread_num)
    }

    //-----------------------------------------------------------
    // Value Declaration
    // Frontend
    //-----------------------------------------------------------
    val f0_valid            = Vec(Bool(), config.thread_num )
    val real_addr           = Vec(UInt(config.extend_addr_width bits), config.thread_num)
    val intrahaz_vec        = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_val        = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_f0         = Vec(Bool(), config.thread_num)
    val intrahaz_poz_s0_f0  = Vec(UInt(3 bits), config.thread_num)
    val intrahaz_poz_add_p1 = Vec(UInt(2 bits), 4)
    val intrahaz_poz_add_p2 = Vec(UInt(3 bits), 2)
    val intrahaz_all_val    = UInt(3 bits)

    val f1_valid            = Vec(Reg(Bool()) init False, config.thread_num )
    val real_addr_valid_f1  = Vec(Reg(Bool()) init False, config.thread_num)
    val edge_value_f1       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val update_ram_addr_f1  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val vertex_reg_addr_f1  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val intrahaz_table_f1   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val interhaz_f1         = Vec(Bits(8 bits), config.thread_num)
    val interhaz_val_f1     = Vec(Bool(), config.thread_num)
    val interhaz_index      = Vec(Vec(Bool(), 3), config.thread_num)
    val interhaz_temp1      = Vec(Bits(4 bits), config.thread_num)
    val interhaz_temp2      = Vec(Bits(2 bits), config.thread_num)

    val intrahaz_adder_table_f1 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val adder_en_f1         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)

    //-----------------------------------------------------------
    // Pipeline
    //-----------------------------------------------------------
    val h1_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val entry_valid_h1      = Vec(Reg(Bool()) init False, config.thread_num)
    val edge_value_h1       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val vertex_reg_data_h1  = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val update_ram_addr_h1  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val interhaz_table_h1   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_table_h1   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_adder_table_h1 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val adder_en_h1         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)
    val multiply_data_h1    = Vec(SInt(20 bits), config.thread_num)

    val h2_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val multiply_data_h2    = Vec(Reg(SInt(20 bits)) init 0, config.thread_num)
    val entry_valid_h2      = Vec(Reg(Bool()) init False, config.thread_num)
    val interhaz_table_h2   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_table_h2   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_adder_table_h2 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val updata_data_old_h2  = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val update_ram_addr_h2  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val normal_haz_data_h2  = Vec(SInt(config.data_width bits), config.thread_num)
    val normal_update_data_h2= Vec(SInt(config.data_width bits), config.thread_num)
    val updata_data_h2      = Vec(SInt(config.data_width bits), config.thread_num)
    val adder_en_h2         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)
    val extra_haz_data_h2   = Vec(SInt(config.data_width bits), config.extra_adder_num)
    val extra_update_data_h2= Vec(SInt(config.data_width bits), config.extra_adder_num)
    val adder_s1_h2         = Vec(Vec(SInt(config.data_width bits), config.thread_num), config.extra_adder_num)

    val h3_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val update_data_h3      = Vec(SInt(config.data_width bits), config.thread_num)
    val ram_data_h3         = Vec(Reg(SInt(config.data_width bits)) init 0, config.thread_num)
    val update_ram_addr_h3  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)

    //-----------------------------------------------------------
    // Misc
    //-----------------------------------------------------------

    val pe_busy             = Reg(Bool()) init False
    val need_new_vertex     = Reg(Bool()) init False
    val fifo_rdy            = Vec(Reg(Bool()) init True, config.thread_num)

    //-----------------------------------------------------------
    // Wiring
    //-----------------------------------------------------------

    io_state.pe_busy            := pe_busy
    io_state.need_new_vertex    := need_new_vertex
    for (i <- 0 until config.thread_num) {
        io_edge.edge_ready(i)       := fifo_rdy(i)
    }

    //-----------------------------------------------------------
    // State Machine
    //-----------------------------------------------------------

    val pe_fsm = new StateMachine {
        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val WAIT = new State
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
                  goto(WAIT)
              }
          }
        WAIT
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
                      fifo_rdy(i)   := True
                  }
                  need_new_vertex   := True
                  goto(IDLE)
              }
          }
    }

    //-----------------------------------------------------------
    // Frontend
    //-----------------------------------------------------------
    // TODO add valid signal for address comparing
    //-----------------------------------------------------------
    // f0  find intra stage hazard
    //-----------------------------------------------------------
    // TODO POWER OVERHEAD HERE, NEED REDESIGN

    for (i <- 0 until config.thread_num) {
        real_addr(i) := (io_edge.tag_value(i) ## io_edge.edge_value(i)(9 downto 4)).asUInt
    }

    for (i <- 0 until config.thread_num - 1) {
        for (j <- i + 1 until config.thread_num) {
            intrahaz_vec(i)(j) := (real_addr(i) === real_addr(j) && f0_valid(i) && f0_valid(j)) ? True | False
        }
        for (k <- 0 until i) {
            intrahaz_val(i)(k) := intrahaz_vec(k)(i)
        }
        intrahaz_f0(i) := intrahaz_vec(i).orR && !intrahaz_val(i).orR
    }

    for (i <- 0 until 4) {
        intrahaz_poz_add_p1(i) :=  intrahaz_f0(i * 2).asUInt +^ intrahaz_f0(i * 2 + 1).asUInt
    }

    for (i <- 0 until 2) {
        intrahaz_poz_add_p2(i) := intrahaz_poz_add_p1(i * 2) +^ intrahaz_poz_add_p1(i * 2 + 1)
    }

    // TODO when remap to adder table, addr need to -1, redundunt logic, need to remove them
    intrahaz_poz_s0_f0(0)   := intrahaz_f0(0).asUInt.resize(3)
    intrahaz_poz_s0_f0(1)   := intrahaz_poz_add_p1(0)
    intrahaz_poz_s0_f0(2)   := intrahaz_poz_add_p1(0) +^ intrahaz_f0(2).asUInt
    intrahaz_poz_s0_f0(3)   := intrahaz_poz_add_p2(0)
    intrahaz_poz_s0_f0(4)   := intrahaz_poz_add_p2(0) +^ intrahaz_f0(4).asUInt
    intrahaz_poz_s0_f0(5)   := intrahaz_poz_add_p2(0) +^ intrahaz_poz_add_p1(2)
    intrahaz_poz_s0_f0(6)   := intrahaz_poz_add_p2(0) +^ intrahaz_poz_add_p1(2) + intrahaz_f0(6).asUInt
    intrahaz_poz_s0_f0(7)   := 0

    intrahaz_all_val        := intrahaz_poz_add_p2(0) +^ intrahaz_poz_add_p2(1)

    //-----------------------------------------------------------
    // pipeline f1: find inter stage hazard WRITE AFTER READ
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        when(io_edge.edge_ready(i) && io_edge.edge_valid(i) && io_edge.edge_value(i) =/= 0) {
            f1_valid(i)             := True
            vertex_reg_addr_f1(i)   := io_edge.edge_value(i)(15 downto 10).asUInt
            update_ram_addr_f1(i)   := real_addr(i)
            edge_value_f1(i)        := io_edge.edge_value(i)(3 downto 0).asSInt
            intrahaz_table_f1(i)    := intrahaz_f0(i).asBits ## intrahaz_poz_s0_f0(i)
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_f1(i)(j) := intrahaz_f0(i) ? intrahaz_vec(i)(j) | False
            }
        } otherwise {
            f1_valid(i)             := False
            vertex_reg_addr_f1(i)   := 0
            update_ram_addr_f1(i)   := 0
            edge_value_f1(i)        := 0
            intrahaz_table_f1(i)    := 0
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_f1(i)(j) := False
            }
        }
    }

    for (i <- 0 until 4) {
        for (j <- 0 until 8) {
            when(intrahaz_f0(i) && intrahaz_poz_s0_f0(j) === i) {
                adder_en_f1(i) := True ## intrahaz_poz_s0_f0(j)
            } otherwise {
                adder_en_f1(i) := 0
            }
        }
    }

    for (i <- 0 until config.thread_num) {
        for (j <- 0 until config.thread_num) {
            interhaz_f1 (i)(j) := ((update_ram_addr_f1(i) === update_ram_addr_h1(j))&&
              real_addr_valid_f1(i) && entry_valid_h1(j)) ? True | False
        }
        interhaz_val_f1(i)      := interhaz_f1 (i).orR
        io_vertex.addr(i)       := vertex_reg_addr_f1(i)
    }

//     TODO is all zero case be excluded
    for (i <- 0 until config.thread_num) {
        interhaz_index(i)(2) := ~interhaz_f1(i)(3 downto 0).orR
        interhaz_temp1(i) := interhaz_index(i)(2) ? interhaz_f1(i)(7 downto 4) | interhaz_f1(i)(3 downto 0);
        interhaz_index(i)(1) := ~interhaz_temp1(i).orR
        interhaz_temp2(i) := interhaz_index(i)(1) ? interhaz_temp1(i)(3 downto 2) | interhaz_temp1(i)(1 downto 0);
        interhaz_index(i)(0) := ~interhaz_temp2(i)(0)
    }

    //-----------------------------------------------------------
    // pipeline h1: Multiplication
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(f1_valid(i)) {
            h1_valid(i)             := True
            entry_valid_h1(i)       := real_addr_valid_f1(i)
            edge_value_h1(i)        := edge_value_f1(i)
            vertex_reg_data_h1(i)   := io_vertex.data(i).asSInt
            update_ram_addr_h1(i)   := update_ram_addr_f1(i)
            interhaz_table_h1(i)    := interhaz_val_f1(i) ? (interhaz_val_f1 (i) ## interhaz_index(i)(2) ## interhaz_index(i)(1) ## interhaz_index(i)(0)) | 0
            intrahaz_table_h1(i)    := intrahaz_table_f1(i)
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h1(i)(j) := intrahaz_adder_table_f1(i)(j)
            }
        } otherwise {
            h1_valid(i)             := False
            entry_valid_h1(i)       := False
            edge_value_h1(i)        := 0
            vertex_reg_data_h1(i)   := 0
            update_ram_addr_h1(i)   := 0
            interhaz_table_h1(i)    := 0
            intrahaz_table_h1(i)    := 0
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h1(i)(j) := False
            }
        }
    }

    for (i <- 0 until config.extra_adder_num) {
        when(adder_en_h1(i)(3)) {
            adder_en_h1(i) := adder_en_f1(i)
        } otherwise {
            adder_en_h1(i) := 0
        }
    }

    for (i <- 0 until config.thread_num) {
    // TODO clip data
        multiply_data_h1(i)     := vertex_reg_data_h1(i) * edge_value_h1(i)
        io_update.rd_addr(i)    := update_ram_addr_h1(i)
    }

    //-----------------------------------------------------------
    // pipeline h2
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(h1_valid(i)) {
            h2_valid(i)             := True
            multiply_data_h2(i)     := multiply_data_h1(i)
            entry_valid_h2(i)       := entry_valid_h1(i)
            interhaz_table_h2(i)    := interhaz_table_h1(i)
            intrahaz_table_h2(i)    := intrahaz_table_h1(i)
            updata_data_old_h2(i)   := io_update.rd_data(i).asSInt
            update_ram_addr_h2 (i)  := update_ram_addr_h1 (i)
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h2(i)(j) := intrahaz_adder_table_h1(i)(j)
            }
        } otherwise {
            h2_valid(i)             := False
            multiply_data_h2(i)     := 0
            entry_valid_h2(i)       := False
            interhaz_table_h2(i)    := 0
            intrahaz_table_h2(i)    := 0
            updata_data_old_h2(i)   := 0
            update_ram_addr_h2(i)   := 0
            for (k <- 0 until config.thread_num) {
                intrahaz_adder_table_h2(i)(k) := False
            }

        }
    }

    for (i <- 0 until config.extra_adder_num) {
        when(adder_en_h2(i)(3)) {
            adder_en_h2(i)          := adder_en_h1(i)
        } otherwise {
            adder_en_h2(i)          := 0
        }
    }

    for (i <- 0 until config.thread_num) {
        normal_haz_data_h2(i) := (interhaz_table_h2(i)(3) && !interhaz_table_h2(i)(3)) ? update_data_h3(interhaz_table_h2(i)(2 downto 0).asUInt) | updata_data_old_h2(i)
        // TODO change
        normal_update_data_h2(i) := (entry_valid_h2(i) && !intrahaz_table_h2(i)(3)) ? (normal_haz_data_h2(i) + multiply_data_h2(i))(config.data_width - 1 downto 0) | 0
        // TODO change
    }

    for (i <- 0 until config.extra_adder_num) {
        extra_haz_data_h2(i) := (interhaz_table_h2(i)(3) && !interhaz_table_h2(i)(3))? update_data_h3(interhaz_table_h2(i)(2 downto 0).asUInt) | updata_data_old_h2(i)
    }

    // TODO Pass intar hazard date into extra adder matrix

    for (i <- 0 until 4) {
    // TODO adder number is 8 here
        for (j <- 0 until 8) {
            when (adder_en_h2(i)(3)) {
                adder_s1_h2(i)(j) :=  intrahaz_adder_table_h2(adder_en_h2(i)(2 downto 0).asUInt)(j) ? multiply_data_h2(j)(15 downto 0) | 0
            } otherwise {
                adder_s1_h2(i)(j) := 0
            }
        }
        // TODO add extra_haz_data_h2 here and sum them up to updata_data_h2
        extra_update_data_h2(i) := adder_en_h2(i)(3) ? adder_s1_h2(i).reduceBalancedTree(_ + _) | 0
    }

    //-----------------------------------------------------------
    // pipeline h3 write back
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(h2_valid(i)) {
            h3_valid(i)             := True
            update_ram_addr_h3(i)   := update_ram_addr_h2(i)
            ram_data_h3(i)          := updata_data_h2(i)
        } otherwise {
            h3_valid(i)             := False
            update_ram_addr_h3(i)   := 0
            ram_data_h3(i)          := 0
        }
    }

    for (i <- 0 until config.thread_num) {
        io_update.wr_valid(i)       := h3_valid(i)
        io_update.wr_addr(i)        := update_ram_addr_h3(i)
        io_update.wr_data(i)        := ram_data_h3(i).asBits
    }
}
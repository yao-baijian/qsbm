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
        val last_update = in Bool()
        val swap_done = in Bool()
        val all_zero = in Bool()
        val pe_busy = out Bool()
        val need_new_vertex = out Bool()
    }

    val io_global_reg = new Bundle {
        val vertex_stream = slave Stream (Bits(config.axi_extend_width bits))
        val reg_full = out Bool()
    }

    val io_update = new Bundle {
        val wr_valid = Vec(out Bool(), config.thread_num)
        val wr_addr = Vec(out UInt (config.extend_addr_width bits), config.thread_num)
        val wr_data = Vec(out Bits (31 bits), config.thread_num)
        val rd_addr = Vec(out UInt (config.extend_addr_width bits), config.thread_num)
        val rd_data = Vec(in Bits (31 bits), config.thread_num)
    }

    val io_fifo = new Bundle {
        val globalreg_done = out Bool()
        val pe_fifo = Vec(slave Stream (Bits(config.data_width bits)), config.thread_num)
        val pe_tag = Vec(slave Stream (Bits(config.tag_width - 1 bits)), config.thread_num)
    }

    val pe_config = PeConfig()
    val global_reg = GlobalReg(pe_config)

    val vertex_addr         = Vec(UInt(config.addr_width bits), config.thread_num)
    val vertex_data         = Vec(Bits(config.data_width bits), config.thread_num)
    val edge_value          = Vec(Bits(config.data_width bits), config.thread_num)
    val tag_value           = Vec(Bits(config.tag_width - 1 bits), config.thread_num)
    val edge_valid          = Vec(Bool(), config.thread_num)
    val pe_busy             = Reg(Bool()) init False
    val need_new_vertex     = Reg(Bool()) init False
    val fifo_rdy            = Vec(Reg(Bool()) init True, config.thread_num)

    //-----------------------------------------------------------
    // Value Declaration
    // Frontend
    //-----------------------------------------------------------
    val f0_valid            = Vec(Bool(), config.thread_num)
    val real_addr_f0        = Vec(UInt(config.extend_addr_width bits), config.thread_num)
    val intrahaz_vec_f0     = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_val_f0     = Vec(Vec(Bool(), config.thread_num), config.thread_num)
    val intrahaz_f0         = Vec(Bool(), config.thread_num)

    val f1_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val entry_valid_f1      = Vec(Reg(Bool()) init False, config.thread_num)
    val edge_value_f1       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val update_ram_addr_f1  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val vertex_reg_addr_f1  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val intrahaz_vec_f1     = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val intrahaz_f1         = Vec(Reg(Bool()) init False, config.thread_num)
    val intrahaz_poz_s0_f1  = Vec(UInt(3 bits), config.thread_num)
    val intrahaz_poz_add_p1_f1 = Vec(UInt(2 bits), 4)
    val intrahaz_poz_add_p2_f1 = Vec(UInt(3 bits), 2)
    val intrahaz_all_val_f1 = UInt(3 bits)

    val f2_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val entry_valid_f2      = Vec(Reg(Bool()) init False, config.thread_num)
    val edge_value_f2       = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val update_ram_addr_f2  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val vertex_reg_addr_f2  = Vec(Reg(UInt(config.addr_width bits)) init 0, config.thread_num)
    val intrahaz_table_f2   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val interhaz_f2         = Vec(Reg(Bits(8 bits)) init 0, config.thread_num)
    val interhaz_val_f2     = Vec(Bool(), config.thread_num)
    val interhaz_index_f2   = Vec(Vec(Bool(), 3), config.thread_num)
    val interhaz_temp1_f2   = Vec(Bits(4 bits), config.thread_num)
    val interhaz_temp2_f2   = Vec(Bits(2 bits), config.thread_num)
    val intrahaz_adder_table_f2 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val adder_en_f2         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)
    val intrahaz_f2         = Vec(Reg(Bool()) init False, config.thread_num)
    val intrahaz_poz_s0_f2  = Vec(Reg(UInt(3 bits)) init 0, config.thread_num)
    //-----------------------------------------------------------
    // Pipeline
    //-----------------------------------------------------------
    val h1_valid = Vec(Reg(Bool()) init False, config.thread_num)
    val entry_valid_h1 = Vec(Reg(Bool()) init False, config.thread_num)
    val edge_value_h1 = Vec(Reg(SInt(config.edge_width bits)) init 0, config.thread_num)
    val vertex_reg_data_h1 = Vec(Reg(SInt(config.data_width bits)), config.thread_num)
    val multiply_data_h1 = Vec(SInt(20 bits), config.thread_num)
    val interhaz_table_h1 = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_table_h1 = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_adder_table_h1 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val update_ram_addr_h1 = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val adder_en_h1         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)

    val h2_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val multiply_data_h2    = Vec(Reg(SInt(20 bits)) init 0 , config.thread_num)
    val entry_valid_h2      = Vec(Reg(Bool()) init False, config.thread_num)
    val intrahaz_table_h2   = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.thread_num)
    val intrahaz_adder_table_h2 = Vec(Vec(Reg(Bool()) init False, config.thread_num), config.thread_num)
    val update_ram_addr_h2  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)
    val forward_data_h2     = Vec(Reg(SInt(config.spmm_prec bits)) init 0, config.thread_num)
    val normal_update_data_h2 = Vec(SInt(config.spmm_prec bits), config.thread_num)
    val updata_data_h2      = Vec(SInt(config.spmm_prec bits), config.thread_num)
    val adder_en_h2         = Vec(Reg(Bits(config.haz_table_width bits)) init 0, config.extra_adder_num)
    val extra_update_data_h2 = Vec(SInt(config.spmm_prec bits), config.extra_adder_num)
    val adder_s1_h2         = Vec(Vec(SInt(config.spmm_prec bits), config.thread_num + 1), config.extra_adder_num)

    val h3_valid            = Vec(Reg(Bool()) init False, config.thread_num)
    val update_data_h3      = Vec(Reg(SInt(config.spmm_prec bits)), config.thread_num)
    val update_ram_addr_h3  = Vec(Reg(UInt(config.extend_addr_width bits)) init 0, config.thread_num)

    //-----------------------------------------------------------
    // Wiring
    //-----------------------------------------------------------

    io_state.pe_busy := pe_busy
    io_state.need_new_vertex := need_new_vertex
    global_reg.io.in_stream <> io_global_reg.vertex_stream
    global_reg.io.srst <> need_new_vertex
    io_fifo.globalreg_done <> global_reg.io.reg_full
    io_global_reg.reg_full <> global_reg.io.reg_full

    for (i <- 0 until config.thread_num) {
        global_reg.io.rd_addr(i) := vertex_addr(i)
        vertex_data(i) := global_reg.io.rd_data(i)
        io_fifo.pe_fifo(i).ready := fifo_rdy(i)
        io_fifo.pe_tag(i).ready := fifo_rdy(i)
        edge_valid(i) := io_fifo.pe_fifo(i).valid
        edge_value(i) := io_fifo.pe_fifo(i).payload
        tag_value(i) := io_fifo.pe_tag(i).payload
    }

    //-----------------------------------------------------------
    // State Machine
    //-----------------------------------------------------------

    val pe_fsm = new StateMachine {
        val IDLE = new State with EntryPoint
        val OPERATE = new State
        val WAIT = new State
        val UPDATE_SUM_AND_SWITCH = new State

        IDLE
          .whenIsActive {
              when(global_reg.io.reg_full) {
                  need_new_vertex := False
              }
              when(io_state.last_update) {
                  for (i <- 0 until config.thread_num) {
                      fifo_rdy(i) := False
                  }
                  goto(UPDATE_SUM_AND_SWITCH)
              } elsewhen (!need_new_vertex && edge_valid.orR === True) {
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
              when(h2_valid.orR === False) {
                  when(io_state.last_update) {
                      for (i <- 0 until config.thread_num) {
                          fifo_rdy(i) := False
                      }
                      goto(UPDATE_SUM_AND_SWITCH)
                  } otherwise {
                      pe_busy := False
                      need_new_vertex := True
                      goto(IDLE)
                  }
              }
          }
        UPDATE_SUM_AND_SWITCH
          .whenIsActive {
              when(io_state.swap_done) {
                  pe_busy := False
                  for (i <- 0 until config.thread_num) {
                      fifo_rdy(i) := True
                  }
                  need_new_vertex := True
                  goto(IDLE)
              }
          }
    }

    //-----------------------------------------------------------
    // Frontend
    // f0  find intra stage hazard
    //-----------------------------------------------------------
    // TODO POWER OVERHEAD?

    for (i <- 0 until config.thread_num) {
        real_addr_f0(i) := ((tag_value(i).asUInt - 1) ## edge_value(i)(9 downto 4)).asUInt
        f0_valid(i)     := fifo_rdy(i) && edge_valid(i) && edge_value(i) =/= 0
    }

    for (i <- 0 until config.thread_num - 1) {
        for (j <- 0 until config.thread_num) {
            if (j <= i) {
                intrahaz_vec_f0(i)(j) := False
            } else {
                intrahaz_vec_f0(i)(j) := ((real_addr_f0(i) === real_addr_f0(j)) && f0_valid(i) && f0_valid(j)) ? True | False
            }
        }
        for (k <- 0 until config.thread_num) {
            if (k < i) {
                intrahaz_val_f0(i)(k) := intrahaz_vec_f0(k)(i)
            } else {
                intrahaz_val_f0(i)(k) := False
            }
        }
        intrahaz_f0(i) := intrahaz_vec_f0(i).orR && !intrahaz_val_f0(i).orR
    }
    intrahaz_f0(config.thread_num - 1) := False

    for (i <- 0 until config.thread_num) {
        intrahaz_vec_f0(config.thread_num - 1)(i) := False
        if (i < config.thread_num - 1) {
            intrahaz_val_f0(config.thread_num - 1)(i) := intrahaz_vec_f0(i)(config.thread_num - 1)
        } else {
            intrahaz_val_f0(config.thread_num - 1)(i) := False
        }
    }

    for (i <- 0 until config.thread_num) {
        when(f0_valid(i)) {
            f1_valid(i)             := True
            vertex_reg_addr_f1(i)   := edge_value(i)(15 downto 10).asUInt
            update_ram_addr_f1(i)   := real_addr_f0(i)
            edge_value_f1(i)        := edge_value(i)(3 downto 0).asSInt
            intrahaz_f1(i)          := intrahaz_f0(i)
            entry_valid_f1(i)       := !intrahaz_val_f0(i).orR
            for (j <- 0 until config.thread_num) {
                intrahaz_vec_f1(i)(j) := intrahaz_vec_f0(i)(j)
            }
        } otherwise {
            f1_valid(i)             := False
            vertex_reg_addr_f1(i)   := 0
            update_ram_addr_f1(i)   := 0
            edge_value_f1(i)        := 0
            intrahaz_f1(i)          := False
            entry_valid_f1(i)       := False
            for (j <- 0 until config.thread_num) {
                intrahaz_vec_f1(i)(j) := False
            }
        }
    }

    for (i <- 0 until 4) {
        intrahaz_poz_add_p1_f1(i) := intrahaz_f1(i * 2).asUInt +^ intrahaz_f1(i * 2 + 1).asUInt
    }

    for (i <- 0 until 2) {
        intrahaz_poz_add_p2_f1(i) := intrahaz_poz_add_p1_f1(i * 2) +^ intrahaz_poz_add_p1_f1(i * 2 + 1)
    }

    // TODO when remap to adder table, addr need to -1, redundunt logic, need to remove them
    intrahaz_poz_s0_f1(0)   := intrahaz_f1(0).asUInt.resize(3)
    intrahaz_poz_s0_f1(1)   := intrahaz_poz_add_p1_f1(0).resize(3)
    intrahaz_poz_s0_f1(2)   := intrahaz_poz_add_p1_f1(0) +^ intrahaz_f1(2).asUInt
    intrahaz_poz_s0_f1(3)   := intrahaz_poz_add_p2_f1(0)
    intrahaz_poz_s0_f1(4)   := intrahaz_poz_add_p2_f1(0) + intrahaz_f1(4).asUInt
    intrahaz_poz_s0_f1(5)   := intrahaz_poz_add_p2_f1(0) + intrahaz_poz_add_p1_f1(2)
    intrahaz_poz_s0_f1(6)   := intrahaz_poz_add_p2_f1(0) + intrahaz_poz_add_p1_f1(2) + intrahaz_f1(6).asUInt
    intrahaz_poz_s0_f1(7)   := 0

    intrahaz_all_val_f1     := intrahaz_poz_add_p2_f1(0) + intrahaz_poz_add_p2_f1(1)

    //-----------------------------------------------------------
    // pipeline f1: find inter stage hazard WRITE AFTER READ
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        when(f1_valid(i)) {
            f2_valid(i)             := True
            vertex_reg_addr_f2(i)   := vertex_reg_addr_f1(i)
            update_ram_addr_f2(i)   := update_ram_addr_f1(i)
            edge_value_f2(i)        := edge_value_f1(i)
            intrahaz_table_f2(i)    := intrahaz_f1(i).asBits ## intrahaz_poz_s0_f1(i)
            entry_valid_f2(i)       := entry_valid_f1(i)
            intrahaz_f2(i)          := intrahaz_f1(i)
            intrahaz_poz_s0_f2(i)   := intrahaz_poz_s0_f1(i)
            for (j <- 0 until config.thread_num) {
                if (i === j) {
                    intrahaz_adder_table_f2(i)(j) := intrahaz_f1(i) ? True | False
                } else {
                    intrahaz_adder_table_f2(i)(j) := intrahaz_f1(i) ? intrahaz_vec_f1(i)(j) | False
                }
            }
        } otherwise {
            f2_valid(i)             := False
            vertex_reg_addr_f2(i)   := 0
            update_ram_addr_f2(i)   := 0
            edge_value_f2(i)        := 0
            intrahaz_table_f2(i)    := 0
            entry_valid_f2(i)       := False
            intrahaz_f2(i)          := False
            intrahaz_poz_s0_f2(i)   := 0
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_f2(i)(j) := False
            }
        }
    }
    for (i <- 0 until 4) {
        when(intrahaz_f2(0) && intrahaz_poz_s0_f2(0) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(0)
        } elsewhen (intrahaz_f2(1) && intrahaz_poz_s0_f2(1) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(1)
        } elsewhen (intrahaz_f2(2) && intrahaz_poz_s0_f2(2) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(2)
        } elsewhen (intrahaz_f2(3) && intrahaz_poz_s0_f2(3) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(3)
        } elsewhen (intrahaz_f2(4) && intrahaz_poz_s0_f2(4) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(4)
        } elsewhen (intrahaz_f2(5) && intrahaz_poz_s0_f2(5) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(5)
        } elsewhen (intrahaz_f2(6) && intrahaz_poz_s0_f2(6) === i + 1) {
            adder_en_f2(i) := True ## intrahaz_poz_s0_f2(6)
        } otherwise {
            adder_en_f2(i) := 0
        }
    }

    for (i <- 0 until config.thread_num) {
        for (j <- 0 until config.thread_num) {
            interhaz_f2(i)(j) := ((update_ram_addr_f2(i) === update_ram_addr_h1(j)) &&
              entry_valid_f2(i) && entry_valid_h1(j)) ? True | False
        }
        interhaz_val_f2(i) := interhaz_f2(i).orR
        vertex_addr(i) := vertex_reg_addr_f2(i)
    }

    //     TODO is all zero case be excluded
    for (i <- 0 until config.thread_num) {
        interhaz_index_f2(i)(2) := ~interhaz_f2(i)(3 downto 0).orR
        interhaz_temp1_f2(i) := interhaz_index_f2(i)(2) ? interhaz_f2(i)(7 downto 4) | interhaz_f2(i)(3 downto 0)
        interhaz_index_f2(i)(1) := ~interhaz_temp1_f2(i).orR
        interhaz_temp2_f2(i) := interhaz_index_f2(i)(1) ? interhaz_temp1_f2(i)(3 downto 2) | interhaz_temp1_f2(i)(1 downto 0)
        interhaz_index_f2(i)(0) := ~interhaz_temp2_f2(i)(0)
    }

    //-----------------------------------------------------------
    // pipeline h1: Multiplication
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        when(f2_valid(i)) {
            h1_valid(i)             := True
            entry_valid_h1(i)       := entry_valid_f2(i)
            edge_value_h1(i)        := edge_value_f2(i)
            vertex_reg_data_h1(i)   := vertex_data(i).asSInt
            interhaz_table_h1(i)    := interhaz_val_f2(i) ? (interhaz_val_f2(i) ## interhaz_index_f2(i)(2) ## interhaz_index_f2(i)(1) ## interhaz_index_f2(i)(0)) | 0
            intrahaz_table_h1(i)    := intrahaz_table_f2(i)
            update_ram_addr_h1(i)   := update_ram_addr_f2(i)
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h1(i)(j) := intrahaz_adder_table_f2(i)(j)
            }
        } otherwise {
            h1_valid(i)             := False
            entry_valid_h1(i)       := False
            edge_value_h1(i)        := 0
            vertex_reg_data_h1(i)   := 0
            interhaz_table_h1(i)    := 0
            intrahaz_table_h1(i)    := 0
            update_ram_addr_h1(i)   := 0
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h1(i)(j) := False
            }
        }
    }

    for (i <- 0 until config.extra_adder_num) {
        adder_en_h1(i) := adder_en_f2(i)
    }

    for (i <- 0 until config.thread_num) {
        multiply_data_h1(i) := edge_value_h1(i) * vertex_reg_data_h1(i)
        io_update.rd_addr(i) := update_ram_addr_h1(i)
    }

    //-----------------------------------------------------------
    // pipeline h2: add back
    //-----------------------------------------------------------

    for (i <- 0 until config.thread_num) {
        when(h1_valid(i)) {
            h2_valid(i)             := True
            entry_valid_h2(i)       := entry_valid_h1(i)
            multiply_data_h2(i)     := multiply_data_h1(i)
            forward_data_h2(i)      := !interhaz_table_h1(i)(3) ? io_update.rd_data(i).asSInt | updata_data_h2(interhaz_table_h1(i)(2 downto 0).asUInt)
            intrahaz_table_h2(i)    := intrahaz_table_h1(i)
            update_ram_addr_h2(i)   := update_ram_addr_h1(i)
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h2(i)(j) := intrahaz_adder_table_h1(i)(j)
            }
        } otherwise {
            h2_valid(i)             := False
            entry_valid_h2(i)       := False
            multiply_data_h2(i)     := 0
            intrahaz_table_h2(i)    := 0
            update_ram_addr_h2(i)   := 0
            for (j <- 0 until config.thread_num) {
                intrahaz_adder_table_h2(i)(j) := False
            }
        }
    }

    for (i <- 0 until config.extra_adder_num) {
        adder_en_h2(i) := adder_en_h1(i)
    }

    for (i <- 0 until config.thread_num) {
        normal_update_data_h2(i) := (entry_valid_h2(i) && !intrahaz_table_h2(i)(3)) ? (forward_data_h2(i) + multiply_data_h2(i)) | 0
    }

    for (i <- 0 until config.extra_adder_num) {
        for (j <- 0 until config.thread_num) {
            when(adder_en_h2(i)(3)) {
                adder_s1_h2(i)(j) := intrahaz_adder_table_h2(adder_en_h2(i)(2 downto 0).asUInt)(j) ? multiply_data_h2(j).resize(31) | 0
            } otherwise {
                adder_s1_h2(i)(j) := 0
            }
        }
        when(adder_en_h2(i)(3)) {
            adder_s1_h2(i)(8) := forward_data_h2(adder_en_h2(i)(2 downto 0).asUInt)
        } otherwise {
            adder_s1_h2(i)(8) := 0
        }
        extra_update_data_h2(i) := adder_en_h2(i)(3) ? adder_s1_h2(i).reduceBalancedTree(_ + _) | 0
    }

    for (i <- 0 until config.thread_num) {
        updata_data_h2(i) := !intrahaz_table_h2(i)(3) ? normal_update_data_h2(i) | extra_update_data_h2((intrahaz_table_h2(i).asUInt - 1)(1 downto 0))
    }

    //-----------------------------------------------------------
    // pipeline h3 write back
    //-----------------------------------------------------------
    for (i <- 0 until config.thread_num) {
        when(h2_valid(i)) {
            h3_valid(i)             := True
            update_ram_addr_h3(i)   := update_ram_addr_h2(i)
            update_data_h3(i)       := updata_data_h2(i)
        } otherwise {
            h3_valid(i)             := False
            update_ram_addr_h3(i)   := 0
            update_data_h3(i)       := 0
        }

        io_update.wr_valid(i) := h3_valid(i)
        io_update.wr_addr(i) := update_ram_addr_h3(i)
        io_update.wr_data(i) := update_data_h3(i).asBits
    }
}
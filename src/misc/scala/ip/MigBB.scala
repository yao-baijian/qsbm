package ip

import spinal.core.{BlackBox, Bundle}
import spinal.core._
import spinal.lib._

//object MigConfig{
//
//  val migDDR4Width = 64
//  val migAxi4Width = 512
//  val ddrAddrWidth = 17
//  val baWidth = 2
//
//}
class MigBB extends BlackBox{

//  import MigConfig._

  val io = new Bundle{

    val c0_init_calib_complete = out Bool() //(c0_init_calib_complete),    // output wire c0_init_calib_complete
    val dbg_clk = out Bool()//(dbg_clk),                                  // output wire dbg_clk
    val c0_sys_clk_i = in Bool()//(c0_sys_clk_i),                        // input wire c0_sys_clk_i
    val dbg_bus = out Bits(512 bits)//(dbg_bus),                                  // output wire [511 : 0] dbg_bus
    val c0_ddr4_adr = in Bits(17 bits) //(c0_ddr4_adr),                          // output wire [16 : 0] c0_ddr4_adr
    val c0_ddr4_ba = out UInt(2 bits) //(c0_ddr4_ba),                            // output wire [1 : 0] c0_ddr4_ba
    val c0_ddr4_cke = out Bool()//(c0_ddr4_cke),                          // output wire [0 : 0] c0_ddr4_cke
    val c0_ddr4_cs_n = out Bool() // (c0_ddr4_cs_n),                        // output wire [0 : 0] c0_ddr4_cs_n
    val c0_ddr4_dm_dbi_n = inout(Bits(8 bits)) //(c0_ddr4_dm_dbi_n),                // inout wire [7 : 0] c0_ddr4_dm_dbi_n
    val c0_ddr4_dq = inout(Bits(64 bits))  //(c0_ddr4_dq),                            // inout wire [63 : 0] c0_ddr4_dq
    val c0_ddr4_dqs_c = inout(Bits(8 bits)) //(c0_ddr4_dqs_c),                      // inout wire [7 : 0] c0_ddr4_dqs_c
    val c0_ddr4_dqs_t=  inout(Bits(8 bits)) //(c0_ddr4_dqs_t),                      // inout wire [7 : 0] c0_ddr4_dqs_t
    val c0_ddr4_odt = out Bool() //(c0_ddr4_odt),                          // output wire [0 : 0] c0_ddr4_odt
    val c0_ddr4_bg = out Bits(1 bits) //(c0_ddr4_bg),                            // output wire [0 : 0] c0_ddr4_bg
    val c0_ddr4_reset_n = out Bool() //(c0_ddr4_reset_n),                  // output wire c0_ddr4_reset_n
    val c0_ddr4_act_n = out Bool() //(c0_ddr4_act_n),                      // output wire c0_ddr4_act_n
    val c0_ddr4_ck_c = out Bool() //(c0_ddr4_ck_c),                        // output wire [0 : 0] c0_ddr4_ck_c
    val c0_ddr4_ck_t = out Bool() //(c0_ddr4_ck_t),                        // output wire [0 : 0] c0_ddr4_ck_t
    val c0_ddr4_ui_clk = out Bool() //(c0_ddr4_ui_clk),                    // output wire c0_ddr4_ui_clk
    val c0_ddr4_ui_clk_sync_rst = out Bool() //(c0_ddr4_ui_clk_sync_rst),  // output wire c0_ddr4_ui_clk_sync_rst
    val c0_ddr4_aresetn = in Bool() //(c0_ddr4_aresetn),                  // input wire c0_ddr4_aresetn
    val c0_ddr4_s_axi_awid = in Bits(4 bits) //(c0_ddr4_s_axi_awid),            // input wire [3 : 0] c0_ddr4_s_axi_awid
    val c0_ddr4_s_axi_awaddr = in Bits(32 bits) //(c0_ddr4_s_axi_awaddr),        // input wire [31 : 0] c0_ddr4_s_axi_awaddr
    val c0_ddr4_s_axi_awlen = in Bits(8 bits)//(c0_ddr4_s_axi_awlen),          // input wire [7 : 0] c0_ddr4_s_axi_awlen
    val c0_ddr4_s_axi_awsize = in Bits (2 bits) //(c0_ddr4_s_axi_awsize),        // input wire [2 : 0] c0_ddr4_s_axi_awsize
    val c0_ddr4_s_axi_awburst = in Bits(2 bits) //(c0_ddr4_s_axi_awburst),      // input wire [1 : 0] c0_ddr4_s_axi_awburst
    val c0_ddr4_s_axi_awlock = in Bool() //(c0_ddr4_s_axi_awlock),        // input wire [0 : 0] c0_ddr4_s_axi_awlock
    val c0_ddr4_s_axi_awcache = in Bits(4 bits) //(c0_ddr4_s_axi_awcache),      // input wire [3 : 0] c0_ddr4_s_axi_awcache
    val c0_ddr4_s_axi_awprot = in Bits(4 bits) //(c0_ddr4_s_axi_awprot),        // input wire [2 : 0] c0_ddr4_s_axi_awprot
    val c0_ddr4_s_axi_awqos = in Bits(4 bits)//(c0_ddr4_s_axi_awqos),          // input wire [3 : 0] c0_ddr4_s_axi_awqos
    val c0_ddr4_s_axi_awvalid = in Bool()//(c0_ddr4_s_axi_awvalid),      // input wire c0_ddr4_s_axi_awvalid
    val c0_ddr4_s_axi_awready = out Bool() //(c0_ddr4_s_axi_awready),      // output wire c0_ddr4_s_axi_awready
    val c0_ddr4_s_axi_wdata = in Bits(512 bits) //(c0_ddr4_s_axi_wdata),          // input wire [511 : 0] c0_ddr4_s_axi_wdata
    val c0_ddr4_s_axi_wstrb = in Bits(64 bits) //(c0_ddr4_s_axi_wstrb),          // input wire [63 : 0] c0_ddr4_s_axi_wstrb
    val c0_ddr4_s_axi_wlast = in Bool()//(c0_ddr4_s_axi_wlast),          // input wire c0_ddr4_s_axi_wlast
    val c0_ddr4_s_axi_wvalid = in Bool()//(c0_ddr4_s_axi_wvalid),        // input wire c0_ddr4_s_axi_wvalid
    val c0_ddr4_s_axi_wready = out Bool()//(c0_ddr4_s_axi_wready),        // output wire c0_ddr4_s_axi_wready
    val c0_ddr4_s_axi_bready = in Bool()//(c0_ddr4_s_axi_bready),        // input wire c0_ddr4_s_axi_bready
    val c0_ddr4_s_axi_bid = out Bits(4 bits)//(c0_ddr4_s_axi_bid),              // output wire [3 : 0] c0_ddr4_s_axi_bid
    val c0_ddr4_s_axi_bresp = out Bits(2 bits)//(c0_ddr4_s_axi_bresp),          // output wire [1 : 0] c0_ddr4_s_axi_bresp
    val c0_ddr4_s_axi_bvalid = out Bool()//(c0_ddr4_s_axi_bvalid),        // output wire c0_ddr4_s_axi_bvalid
    val c0_ddr4_s_axi_arid = in Bits(4 bits)             // input wire [3 : 0] c0_ddr4_s_axi_arid
    val c0_ddr4_s_axi_araddr = in Bits(32 bits)        // input wire [31 : 0] c0_ddr4_s_axi_araddr
    val c0_ddr4_s_axi_arlen = in Bits(8 bits)          // input wire [7 : 0] c0_ddr4_s_axi_arlen
    val c0_ddr4_s_axi_arsize = in Bits(3 bits)        // input wire [2 : 0] c0_ddr4_s_axi_arsize
    val c0_ddr4_s_axi_arburst = in Bits(2 bits)        // input wire [1 : 0] c0_ddr4_s_axi_arburst
    val c0_ddr4_s_axi_arlock = in Bool()         // input wire [0 : 0] c0_ddr4_s_axi_arlock
    val c0_ddr4_s_axi_arcache = in Bits(4 bits)      // input wire [3 : 0] c0_ddr4_s_axi_arcache
    val c0_ddr4_s_axi_arprot = in Bits(3 bits)        // input wire [2 : 0] c0_ddr4_s_axi_arprot
    val c0_ddr4_s_axi_arqos = in Bits(4 bits)          // input wire [3 : 0] c0_ddr4_s_axi_arqos
    val c0_ddr4_s_axi_arvalid = in Bool()      // input wire c0_ddr4_s_axi_arvalid
    val c0_ddr4_s_axi_arready = out Bool()       // output wire c0_ddr4_s_axi_arready
    val c0_ddr4_s_axi_rready = in Bool()        // input wire c0_ddr4_s_axi_rready
    val c0_ddr4_s_axi_rlast = out Bool()          // output wire c0_ddr4_s_axi_rlast
    val c0_ddr4_s_axi_rvalid = out Bool()                  // output wire c0_ddr4_s_axi_rvalid
    val c0_ddr4_s_axi_rresp = out Bits(2 bits)             // output wire [1 : 0] c0_ddr4_s_axi_rresp
    val c0_ddr4_s_axi_rid = out Bits(4 bits)              // output wire [3 : 0] c0_ddr4_s_axi_rid
    val c0_ddr4_s_axi_rdata  = out Bits(512 bits)          // output wire [511 : 0] c0_ddr4_s_axi_rdata
    val sys_rst = in Bool()                                    // input wire sys_rst
  }
  noIoPrefix()

}



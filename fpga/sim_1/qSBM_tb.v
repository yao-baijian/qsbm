`timescale 1ns / 1ps //200MHz
//////////////////////////////////////////////////////////////////////////////////
// Company: 
// Engineer: 
// 
// Create Date: 09/19/2024 03:48:45 PM
// Design Name: 
// Module Name: SboomTop_tb
// Project Name: 
// Target Devices: 
// Tool Versions: 
// Description: 
// 
// Dependencies: 
// 
// Revision:
// Revision 0.01 - File Created
// Additional Comments:
// 
//////////////////////////////////////////////////////////////////////////////////


module qSBM_tb(
    );
    wire          tb_M00_AXI_AWVALID;
    reg          	tb_M00_AXI_AWREADY;
    wire [31:0]   tb_M00_AXI_AWADDR;
    wire [3:0]    tb_M00_AXI_AWID;
    wire [3:0]    tb_M00_AXI_AWREGION;
    wire [7:0]    tb_M00_AXI_AWLEN;
    wire [2:0]    tb_M00_AXI_AWSIZE;
    wire [1:0]    tb_M00_AXI_AWBURST;
    wire [0:0]    tb_M00_AXI_AWLOCK;
    wire [3:0]    tb_M00_AXI_AWCACHE;
    wire [3:0]    tb_M00_AXI_AWQOS;
    wire [2:0]    tb_M00_AXI_AWPROT;
    
    wire          tb_M00_AXI_WVALID;
    reg          	tb_M00_AXI_WREADY;
    wire [511:0]  tb_M00_AXI_WDATA;
    wire [63:0]   tb_M00_AXI_WSTRB;
    wire          tb_M00_AXI_WLAST;
    
    reg          	tb_M00_AXI_BVALID;
    wire          tb_M00_AXI_BREADY;
    reg [3:0]    	tb_M00_AXI_BID;
    reg [1:0]    	tb_M00_AXI_BRESP;
    
    wire          tb_M00_AXI_ARVALID;
    reg          	tb_M00_AXI_ARREADY;
    wire [31:0]   tb_M00_AXI_ARADDR;
    wire [3:0]    tb_M00_AXI_ARID;
    wire [3:0]    tb_M00_AXI_ARREGION;
    wire [7:0]    tb_M00_AXI_ARLEN;
    wire [2:0]    tb_M00_AXI_ARSIZE;
    wire [1:0]    tb_M00_AXI_ARBURST;
    wire [0:0]    tb_M00_AXI_ARLOCK;
    wire [3:0]    tb_M00_AXI_ARCACHE;
    wire [3:0]    tb_M00_AXI_ARQOS;
    wire [2:0]    tb_M00_AXI_ARPROT;
    
    reg          	tb_M00_AXI_RVALID;
    wire          tb_M00_AXI_RREADY;
    reg [511:0]  	tb_M00_AXI_RDATA;
    reg [3:0]    	tb_M00_AXI_RID;
    reg [1:0]    	tb_M00_AXI_RRESP;
    reg          	tb_M00_AXI_RLAST;
    
    reg          	tb_S00_AXI_AWVALID;
    wire          tb_S00_AXI_AWREADY;
    reg [31:0]   	tb_S00_AXI_AWADDR;
    reg [2:0]    	tb_S00_AXI_AWPROT;
    reg          	tb_S00_AXI_WVALID;
    wire          tb_S00_AXI_WREADY;
    reg [31:0]   	tb_S00_AXI_WDATA;
    reg [3:0]    	tb_S00_AXI_WSTRB;
    
    wire          tb_S00_AXI_BVALID;
    reg          	tb_S00_AXI_BREADY;
    wire [1:0]    tb_S00_AXI_BRESP;
    
    reg          	tb_S00_AXI_ARVALID;
    wire          tb_S00_AXI_ARREADY;
    reg [31:0]   	tb_S00_AXI_ARADDR;
    reg [2:0]    	tb_S00_AXI_ARPROT;
    
    wire          tb_S00_AXI_RVALID;
    reg          	tb_S00_AXI_RREADY;
    wire [31:0]   tb_S00_AXI_RDATA;
    wire [1:0]    tb_S00_AXI_RRESP;
    
    reg          	tb_clk;
    reg          	tb_reset;
  
  qSBM dut(
	.M00_AXI_AWVALID(tb_M00_AXI_AWVALID),
	.M00_AXI_AWREADY(tb_M00_AXI_AWREADY),
	.M00_AXI_AWADDR(tb_M00_AXI_AWADDR),
	.M00_AXI_AWID(tb_M00_AXI_AWID),
	.M00_AXI_AWREGION(tb_M00_AXI_AWREGION),
	.M00_AXI_AWLEN(tb_M00_AXI_AWLEN),
	.M00_AXI_AWSIZE(tb_M00_AXI_AWSIZE),
	.M00_AXI_AWBURST(tb_M00_AXI_AWBURST),
	.M00_AXI_AWLOCK(tb_M00_AXI_AWLOCK),
	.M00_AXI_AWCACHE(tb_M00_AXI_AWCACHE),
	.M00_AXI_AWQOS(tb_M00_AXI_AWQOS),
	.M00_AXI_AWPROT(tb_M00_AXI_AWPROT),
	
	.M00_AXI_WVALID(tb_M00_AXI_WVALID),
	.M00_AXI_WREADY(tb_M00_AXI_WREADY),
	.M00_AXI_WDATA(tb_M00_AXI_WDATA),
	.M00_AXI_WSTRB(tb_M00_AXI_WSTRB),
	.M00_AXI_WLAST(tb_M00_AXI_WLAST),
	
	.M00_AXI_BVALID(tb_M00_AXI_BVALID),
	.M00_AXI_BREADY(tb_M00_AXI_BREADY),
	.M00_AXI_BID(tb_M00_AXI_BID),
	.M00_AXI_BRESP(tb_M00_AXI_BRESP),
	
	.M00_AXI_ARVALID(tb_M00_AXI_ARVALID),
	.M00_AXI_ARREADY(tb_M00_AXI_ARREADYtb_M00_AXI_ARADDR),
	.M00_AXI_ARADDR(tb_M00_AXI_ARADDR),
	.M00_AXI_ARID(tb_M00_AXI_ARID),
	.M00_AXI_ARREGION(tb_M00_AXI_ARREGION),
	.M00_AXI_ARLEN(tb_M00_AXI_ARLEN),
	.M00_AXI_ARSIZE(tb_M00_AXI_ARSIZE),
	.M00_AXI_ARBURST(tb_M00_AXI_ARBURST),
	.M00_AXI_ARLOCK(tb_M00_AXI_ARLOCK),
	.M00_AXI_ARCACHE(tb_M00_AXI_ARCACHE),
	.M00_AXI_ARQOS(tb_M00_AXI_ARQOS),
	.M00_AXI_ARPROT(tb_M00_AXI_ARPROT),
	
	.M00_AXI_RVALID(tb_M00_AXI_RVALID),
	.M00_AXI_RREADY(tb_M00_AXI_RREADY),
	.M00_AXI_RDATA(tb_M00_AXI_RDATA),
	.M00_AXI_RID(tb_M00_AXI_RID),
	.M00_AXI_RRESP(tb_M00_AXI_RRESP),
	.M00_AXI_RLAST(tb_M00_AXI_RLAST),
	
	.S00_AXI_AWVALID(tb_S00_AXI_AWVALID),
	.S00_AXI_AWREADY(tb_S00_AXI_AWREADY),
	.S00_AXI_AWADDR(tb_S00_AXI_AWADDR),
	.S00_AXI_AWPROT(tb_S00_AXI_AWPROT),
	
	.S00_AXI_WVALID(tb_S00_AXI_WVALID),
	.S00_AXI_WREADY(tb_S00_AXI_WREADY),
	.S00_AXI_WDATA(tb_S00_AXI_WDATA),
	.S00_AXI_WSTRB(tb_S00_AXI_WSTRB),
	
	.S00_AXI_BVALID(tb_S00_AXI_BVALID),
	.S00_AXI_BREADY(tb_S00_AXI_BREADY),
	.S00_AXI_BRESP(tb_S00_AXI_BRESP),
	
	.S00_AXI_ARVALID(tb_S00_AXI_ARVALID),
	.S00_AXI_ARREADY(tb_S00_AXI_ARREADY),
	.S00_AXI_ARADDR(tb_S00_AXI_ARADDR),
	.S00_AXI_ARPROT(tb_S00_AXI_ARPROT),
	
	.S00_AXI_RVALID(tb_S00_AXI_RVALID),
	.S00_AXI_RREADY(tb_S00_AXI_RREADY),
	.S00_AXI_RDATA(tb_S00_AXI_RDATA),
	.S00_AXI_RRESP(tb_S00_AXI_RRESP),
	
	.clk(tb_clk),
	.reset(tb_reset)
);

initial begin
    tb_clk = 1;
    forever #1 tb_clk = ~tb_clk;
end

initial begin
    // reset
    tb_reset = 0; #10
    tb_reset = 1;
    // config AWPROT
    tb_S00_AXI_AWPROT = 3'b001; // unpriviledged, secure, instruction access
    tb_S00_AXI_WSTRB = 4'd0;    // no narrow transfers
    tb_S00_AXI_BREADY = 1'b1;
    // iteration
    tb_S00_AXI_AWADDR = 8'h0C;
    tb_S00_AXI_WDATA = 32'd100;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // matrix_size
    tb_S00_AXI_AWADDR = 8'h10;
    tb_S00_AXI_WDATA = 32'd2000;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // tile_xy
    tb_S00_AXI_AWADDR = 8'h14;
    tb_S00_AXI_WDATA = 32'd64;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // CB_max
    tb_S00_AXI_AWADDR = 8'h18;
    tb_S00_AXI_WDATA = 32'd32;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // CB_init
//    tb_S00_AXI_AWADDR = 8'h14;
//    tb_S00_AXI_WDATA = 32'd32;
//    tb_S00_AXI_AWVALID = 1'b1;
//    tb_S00_AXI_WVALID = 1'b1;
//    #1
//    while (tb_S00_AXI_BRESP != 2'b00) #1;
//    tb_S00_AXI_AWVALID = 1'b0;
//    tb_S00_AXI_WVALID = 1'b0;
    
    // RB_init
//    tb_S00_AXI_AWADDR = 8'h20;
//    tb_S00_AXI_WDATA = 32'd32;
//    tb_S00_AXI_AWVALID = 1'b1;
//    tb_S00_AXI_WVALID = 1'b1;
//    #1
//    while (tb_S00_AXI_BRESP != 2'b00) #1;
//    tb_S00_AXI_AWVALID = 1'b0;
//    tb_S00_AXI_WVALID = 1'b0;
    
    // ai_init
    tb_S00_AXI_AWADDR = 8'h24;
    tb_S00_AXI_WDATA = 32'd0;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // ai_incr
    tb_S00_AXI_AWADDR = 8'h28;
    tb_S00_AXI_WDATA = 32'd1;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // xi
    tb_S00_AXI_AWADDR = 8'h2c;
    tb_S00_AXI_WDATA = 32'd1;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // dt
    tb_S00_AXI_AWADDR = 8'h30;
    tb_S00_AXI_WDATA = 32'd16;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // vex_a_base
    tb_S00_AXI_AWADDR = 8'h34;
    tb_S00_AXI_WDATA = 32'd0;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // vex_b_base
    tb_S00_AXI_AWADDR = 8'h38;
    tb_S00_AXI_WDATA = 32'h400000;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // edge_base
    tb_S00_AXI_AWADDR = 8'h3c;
    tb_S00_AXI_WDATA = 32'h800000;
    tb_S00_AXI_AWVALID = 1'b1;
    tb_S00_AXI_WVALID = 1'b1;
    #1
    while (tb_S00_AXI_BRESP != 2'b00) #1;
    tb_S00_AXI_AWVALID = 1'b0;
    tb_S00_AXI_WVALID = 1'b0;
    
    // RB_max
//    tb_S00_AXI_AWADDR = 8'h40;
//    tb_S00_AXI_WDATA = 32';
//    tb_S00_AXI_AWVALID = 1'b1;
//    tb_S00_AXI_WVALID = 1'b1;
//    #1
//    while (tb_S00_AXI_BRESP != 2'b00) #1;
//    tb_S00_AXI_AWVALID = 1'b0;
//    tb_S00_AXI_WVALID = 1'b0;
    
    // CB_length
//    tb_S00_AXI_AWADDR = 8'h44;
//    tb_S00_AXI_WDATA = 32';
//    tb_S00_AXI_AWVALID = 1'b1;
//    tb_S00_AXI_WVALID = 1'b1;
//    #1
//    while (tb_S00_AXI_BRESP != 2'b00) #1;
//    tb_S00_AXI_AWVALID = 1'b0;
//    tb_S00_AXI_WVALID = 1'b0;
end

endmodule
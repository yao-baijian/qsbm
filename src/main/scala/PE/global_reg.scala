case class global_reg(

    
) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(128))
        val rd_addr   = in Bits(6 bits)
        val rd_data   = out Bits(16)
    }

    val vertex_reg = Vec(Reg(UInt(16 bits)), 64)
    val wr_pointer = Reg(UInt(4 bits)) init 0

    when(rst) {
        wr_pointer := 0b0
    }.elsewhen (in_stream.valid && in_stream.ready) {
        wr_pointer := wr_pointer + 1
    }.otherwise {
        wr_pointer := 0b0
    }

    when(rst) {
        vertex_reg.foreach(_ := 0)
    }.elsewhen (in_stream.valid && in_stream.ready) {
        vertex_reg(wr_pointer) := in_stream.payload(127 downto 112)
        vertex_reg(wr_pointer + 1) := in_stream.payload(111 downto 96)
        vertex_reg(wr_pointer + 2) := in_stream.payload(95 downto 80)
        vertex_reg(wr_pointer + 3) := in_stream.payload(79 downto 64)
        vertex_reg(wr_pointer + 4) := in_stream.payload(63 downto 48)
        vertex_reg(wr_pointer + 5) := in_stream.payload(47 downto 32)
        vertex_reg(wr_pointer + 6) := in_stream.payload(31 downto 16)
        vertex_reg(wr_pointer + 7) := in_stream.payload(15 downto 0)
    }

    io.rd_data := vertex_reg(io.rd_addr)

//     reg [3:0] finish_num;// the number of finish fifo data to reg
//     always@(posedge clk, negedge rst) begin
//         if(~rst) begin
//             finish_num<=0;
//             vertex_write_fnish<=0;
//         end
//         else if( push_valid && i==56) begin
//             finish_num<=finish_num+1;
//             vertex_write_fnish<=1;
//         end
//         else begin
//             vertex_write_fnish<=0;
//         end
//     end

//     reg [5:0] update_cnt;//initially move the value of the reg after the first to eight assignments to ram,when finish whole update once , 9-16 reg to ram
//     always@(posedge clk, negedge rst) begin
//         if(~rst) begin
//             update_cnt<=0;
//         end
//         else if(whole_update_finish) begin
//             update_cnt<=update_cnt+1;
//         end
//     end
// //use finish whole update to change finish num
//     assign vertex_reg_finish[3]=(finish_num==(update_cnt<<2) &&  push_valid && i==56); //to the first PE
//     assign vertex_reg_finish[2]=(finish_num==(update_cnt<<2)+1 &&  push_valid && i==56); //to the second PE
//     assign vertex_reg_finish[1]=(finish_num==(update_cnt<<2)+2 &&  push_valid && i==56); //to the third PE
//     assign vertex_reg_finish[0]=(finish_num==(update_cnt<<2)+3 &&  push_valid && i==56); //to the 4th PE
}






// module fifo2reg(
//     input clk,
//     input rst,
// //ports to ddr
//     input push_valid,
//     input [127:0] push_data,
// //ports to 4 PE
// //1
//     input [5:0] read_addr_vertex_reg_pe_1,
//     input vertex_reg_enb_pe_1,
//     output reg [15:0]  dout_vertex_reg_pe_1,
//     input [5:0] read_addr_vertex_reg_reg2ram_1,
//     input vertex_reg_enb_reg2ram_1,
//     output reg [15:0]  dout_vertex_reg_reg2ram_1,
// //2
//     input [5:0] read_addr_vertex_reg_pe_2,
//     input vertex_reg_enb_pe_2,
//     output reg [15:0] dout_vertex_reg_pe_2,
//     input [5:0] read_addr_vertex_reg_reg2ram_2,
//     input vertex_reg_enb_reg2ram_2,
//     output reg [15:0] dout_vertex_reg_reg2ram_2,
// //3
//     input [5:0] read_addr_vertex_reg_pe_3,
//     input vertex_reg_enb_pe_3,
//     output reg [15:0] dout_vertex_reg_pe_3,
//     input [5:0] read_addr_vertex_reg_reg2ram_3,
//     input vertex_reg_enb_reg2ram_3,
//     output reg [15:0] dout_vertex_reg_reg2ram_3,
// //4
//     input [5:0] read_addr_vertex_reg_pe_4,
//     input vertex_reg_enb_pe_4,
//     output reg [15:0] dout_vertex_reg_pe_4,
//     input [5:0] read_addr_vertex_reg_reg2ram_4,
//     input vertex_reg_enb_reg2ram_4,
//     output reg [15:0] dout_vertex_reg_reg2ram_4,
// //ports to reg2ram
//     output [3:0] vertex_reg_finish,
// //ports to PE
//     output reg vertex_write_fnish,
// //ports from PE
//     input whole_update_finish
// );
// //output to 8 PE
//   reg [15:0] mem [0:63];
// //1
//     always@(posedge clk,negedge rst) begin
//         if(~rst) begin
//             dout_vertex_reg_pe_1<=0;
//         end
//         else if(vertex_reg_enb_pe_1) begin
//             dout_vertex_reg_pe_1<=mem[read_addr_vertex_reg_pe_1];
//         end
//     end
//     always@(posedge clk,negedge rst) begin
//         if(~rst) begin
//             dout_vertex_reg_reg2ram_1<=0;
//         end
//         else if(vertex_reg_enb_reg2ram_1) begin
//             dout_vertex_reg_reg2ram_1<=mem[read_addr_vertex_reg_reg2ram_1];
//         end
//     end
// //2
//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_pe_2 <= 0;
//         end
//         else if (vertex_reg_enb_pe_2) begin
//             dout_vertex_reg_pe_2 <= mem[read_addr_vertex_reg_pe_2];
//         end
//     end

//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_reg2ram_2 <= 0;
//         end
//         else if (vertex_reg_enb_reg2ram_2) begin
//             dout_vertex_reg_reg2ram_2 <= mem[read_addr_vertex_reg_reg2ram_2];
//         end
//     end
// //3
//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_pe_3 <= 0;
//         end
//         else if (vertex_reg_enb_pe_3) begin
//             dout_vertex_reg_pe_3 <= mem[read_addr_vertex_reg_pe_3];
//         end
//     end

//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_reg2ram_3 <= 0;
//         end
//         else if (vertex_reg_enb_reg2ram_3) begin
//             dout_vertex_reg_reg2ram_3 <= mem[read_addr_vertex_reg_reg2ram_3];
//         end
//     end
// //4
//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_pe_4 <= 0;
//         end
//         else if (vertex_reg_enb_pe_4) begin
//             dout_vertex_reg_pe_4 <= mem[read_addr_vertex_reg_pe_4];
//         end
//     end

//     always @(posedge clk, negedge rst) begin
//         if (~rst) begin
//             dout_vertex_reg_reg2ram_4 <= 0;
//         end
//         else if (vertex_reg_enb_reg2ram_4) begin
//             dout_vertex_reg_reg2ram_4 <= mem[read_addr_vertex_reg_reg2ram_4];
//         end
//     end





// endmodule
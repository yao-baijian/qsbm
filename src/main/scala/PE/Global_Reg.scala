package PE

import spinal.core._
import spinal.lib._

case class Global_Reg(

   stream_width: Int = 128,
   reg_depth: Int = 64,
   rd_addr_width: Int = 6,
   data_width: Int = 16

) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(stream_width bits))
        val rd_addr   = in Bits(rd_addr_width bits)
        val sw_done   = in Bool()

        val rd_data   = out Bits(data_width bits)
        val reg_full  = out Bool()
    }

    val io_vertex_ram = new Bundle {
        val wr_addr_to_ram = out Bits(6 bits)
        val wr_data_to_ram = out Bits(16 bits)
        val rd_addr_to_ram = out Bits(6 bits)
        val rd_data_from_ram = in Bits(16 bits)
    }


    val vertex_reg = Vec(Reg(UInt(data_width bits)) init(0), reg_depth)
    val wr_pointer = Reg(UInt(3 bits)) init 0

    when(io.in_stream.valid && io.in_stream.ready) {
        wr_pointer := wr_pointer + 1
    }.otherwise {
        wr_pointer := 0
    }

    when (wr_pointer === reg_depth) {
        io.reg_full := True
    }.otherwise {
        io.reg_full := False
    }

    when (io.reg_full) {
        in_stream.ready := False
    }. elsewhen (sw_done)
        in_stream.ready := True
    otherwise {
        in_stream.ready := in_stream.ready
    }

    when(in_stream.valid && in_stream.ready) {
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
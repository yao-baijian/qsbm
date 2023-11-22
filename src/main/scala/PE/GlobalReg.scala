package PE

import spinal.core._
import spinal.lib._

case class GlobalReg(

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
        io.in_stream.ready := False
    } .elsewhen (sw_done) {
        io.in_stream.ready := True
    } .otherwise {
        io.in_stream.ready := io.in_stream.ready
    }


    when(io.in_stream.valid && io.in_stream.ready) {
        for (x <- 0 until 8) {
            vertex_reg(wr_pointer + x) := io.in_stream.payload(16 * x - 1 downto 16 * (x - 1))
        }
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
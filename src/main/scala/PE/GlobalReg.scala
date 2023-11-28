package PE

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class GlobalReg(config: GlobalRegConfig) extends Component{

    val io = new Bundle{
        val in_stream = slave Stream(Bits(config.stream_width bits))

        val rd_addr   = Vec(in UInt(config.addr_width bits), 8)
        val rd_data   = Vec(out Bits(config.data_width bits), 8)

        val need_new_vertex   = in Bool()
        val reg_full  = out Bool()
    }
//    val vertex_reg = Vec(Bits(config.data_width bits), config.reg_depth)
//    val vertex_reg = Vec(Reg(Bits(config.data_width bits)), config.reg_depth)
    val vertex_reg = Vec.fill(config.reg_depth)(Reg(Bits(config.data_width bits)))
    vertex_reg.foreach(_ init(0))
//    val vertex_reg = Vec(Reg(Bits(config.data_width bits)))  init (0)
//    val vertex_reg = new Array[Bits] (64)
//    for (i <- 0 until 63) {
//        vertex_reg(i) = Reg(Bits(config.data_width bits)) init(0)
//    }

    val wr_pointer = Reg(UInt(3 bits)) init (0)
//
//    var test = ((wr_pointer*8)(5 downto 0) + U(1)).resized
//    println(test)

//    println(wr_pointer.resized)

    when (wr_pointer === 7) {
        io.in_stream.ready := False
    } elsewhen (io.need_new_vertex) {
        io.in_stream.ready := True
    }

    io.reg_full := !io.in_stream.ready

    when(io.in_stream.valid && io.in_stream.ready) {
        for (i <- 0 until 7) {
//            println(wr_pointer * U(8))
//            println(vertex_reg(0))
//            println(wr_pointer)
//            vertex_reg(wr_pointer.resized * 8 + x) := io.in_stream.payload(16*(x + 1) - 1 downto 16*x )
            vertex_reg((wr_pointer*8)(5 downto 0) + i) := io.in_stream.payload(16*(i + 1) - 1 downto 16*i )
        }
        wr_pointer := wr_pointer + 1
    } otherwise {
        wr_pointer := 0
    }

    for (i <- 0 until 7) {
        io.rd_data (i) := vertex_reg(io.rd_addr(i))
    }


}
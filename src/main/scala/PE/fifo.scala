case class global_reg(
    reg_depth: Int = 64,
    reg_width: Int = 128

) extends Component{
    val io = new Bundle{
        val in_stream = slave Stream(Bits(reg_width))
        val out_stream = master Stream(Bits(reg_width))
    }

    val edge_Fifo = StreamFifo(Bits(reg_width), reg_depth)
    val input_adge_valid = Reg(Bool) init True

    when (slave.payload == 0x0000000000000000) {
        input_adge_valid := False
    }.elsewhen (!in_stream.valid) {
        input_adge_valid := True
    } otherwise {
        input_adge_valid := input_adge_valid
    }

    edge_Fifo.io.push.valid := in_stream.valid && input_adge_valid
    edge_Fifo.io.push.payload := in_stream.payload
    in_stream.ready := edge_Fifo.io.push.ready
    edge_Fifo.io.pop << io.out_stream

}
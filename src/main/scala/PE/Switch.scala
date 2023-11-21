package PE
import spinal.core._

case class Switch (

		data_width: Int = 16,
    addr_width: Int = 6
	
) extends Component {

	val io = new Bundle {
		val switch_nxt  	= in Bool()
		val switch_done  	= out Bool()
	}

	val io_A = new Bundle {
		val wr_valid_from_A = in Bool()
		val wr_addr_from_A = in UInt(addr_width bits)
		val wr_data_from_A = in Bits(data_width bits)
		val rd_addr_from_A = in UInt(addr_width bits)
		val rd_data_to_A = out Bits(data_width bits)
	}

	val io_B = new Bundle {
		val wr_valid_from_B = in Bool()
		val wr_addr_from_B = in UInt(addr_width bits)
		val wr_data_from_B = in Bits(data_width bits)
		val rd_addr_from_B = in UInt(addr_width bits)
		val rd_data_to_B = out Bits(data_width bits)
	}

	val io_ramA = new Bundle {
		val wr_valid_to_ramA = in Bool()
		val wr_addr_to_ramA = out UInt(addr_width bits)
		val wr_data_to_ramA = out Bits(data_width bits)
		val rd_addr_to_ramA = out UInt(addr_width bits)
		val rd_data_from_ramA = in Bits(data_width bits)
	}

	val io_ramB = new Bundle {
		val wr_valid_to_ramB = in Bool()
		val wr_addr_to_ramB = out UInt(addr_width bits)
		val wr_data_to_ramB = out Bits(data_width bits)
		val rd_addr_to_ramB = out UInt(addr_width bits)
		val rd_data_from_ramB = in Bits(data_width bits)
	}

	val switch         	  = Reg(Bool()) init True

	when(io.switch_nxt) {
		switch := ~switch
		io.switch_done := True
	}.otherwise {
		io.switch_done := False
	}

	io_ramA.wr_valid_to_ramA := switch ? io_A.wr_valid_from_A | io_B.wr_valid_from_B
	io_ramA.wr_addr_to_ramA := switch ? io_A.wr_addr_from_A | io_B.wr_addr_from_B
	io_ramA.wr_data_to_ramA := switch ? io_A.wr_data_from_A | io_B.wr_data_from_B
	io_ramA.rd_addr_to_ramA := switch ? io_A.rd_addr_from_A | io_B.rd_addr_from_B
	io_ramA.rd_data_from_ramA := switch ? io_A.rd_data_to_A | io_B.rd_data_to_B

	io_ramB.wr_valid_to_ramB := switch ? io_B.wr_valid_from_B | io_A.wr_valid_from_A
	io_ramB.wr_addr_to_ramB := switch ? io_B.wr_addr_from_B | io_A.wr_addr_from_A
	io_ramB.wr_data_to_ramB := switch ? io_B.wr_data_from_B | io_A.wr_data_from_A
	io_ramB.rd_addr_to_ramB := switch ? io_B.rd_addr_from_B | io_A.rd_addr_from_A
	io_ramB.rd_data_from_ramB := switch ? io_B.rd_data_to_B | io_A.rd_data_to_A

}

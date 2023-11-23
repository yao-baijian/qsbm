package PE

import spinal.core._
import spinal.lib._
case class SwitchReg(

		data_width: Int = 16,
    addr_width: Int = 6
	
) extends Component {

	val io = new Bundle {
		val switch_nxt  	= in Bool()
		val switch_done  	= out Bool()
	}

	val io_from_dispatcher = new Bundle {
		val stream_from_dispatcher = slave Stream(Bits(data_width bits))
	}

	val io_from_pe = new Bundle {
		val rd_addr_from_B = in UInt(addr_width bits)
		val rd_data_to_B = out Bits(data_width bits)
	}

	val io_from_gather_pe = new Bundle {
		val wr_valid 	= out UInt (addr_width bits)
		val wr_addr 	= out UInt (addr_width bits)
		val wr_data	 	= out Bits (data_width bits)
		val rd_addr 	= in UInt (addr_width bits)
		val rd_data		= out Bits (data_width bits)
	}

	val io_to_regA = new Bundle {
		val stream_from_dispatcher = master Stream(Bits(data_width bits))
		val pe_rd_addr = in UInt (addr_width bits)
		val pe_rd_data = out Bits (data_width bits)

		val gather_pe_wr_valid = out UInt (addr_width bits)
		val gather_pe_wr_addr = out UInt (addr_width bits)
		val gather_pe_wr_data = out Bits (data_width bits)
		val gather_pe_rd_addr = in UInt (addr_width bits)
		val gather_pe_rd_data = out Bits (data_width bits)
	}

	val io_to_regB = new Bundle {
		val stream_from_dispatcher = slave Stream (Bits(data_width bits))
		val pe_rd_addr = in UInt (addr_width bits)
		val pe_rd_data = out Bits (data_width bits)

		val gather_pe_wr_valid = out UInt (addr_width bits)
		val gather_pe_wr_addr = out UInt (addr_width bits)
		val gather_pe_wr_data = out Bits (data_width bits)
		val gather_pe_rd_addr = in UInt (addr_width bits)
		val gather_pe_rd_data = out Bits (data_width bits)
	}

	val switch         	  = Reg(Bool()) init True

	when(io.switch_nxt) {
		switch := ~switch
		io.switch_done := True
	}.otherwise {
		io.switch_done := False
	}

	val stream_from_dispatcher = slave Stream (Bits(data_width bits))
	val pe_rd_addr = in UInt (addr_width bits)
	val pe_rd_data = out Bits (data_width bits)

	val gather_pe_wr_valid = out UInt (addr_width bits)
	val gather_pe_wr_addr = out UInt (addr_width bits)
	val gather_pe_wr_data = out Bits (data_width bits)
	val gather_pe_rd_addr = in UInt (addr_width bits)
	val gather_pe_rd_data = out Bits (data_width bits)

	io_from_dispatcher.stream_from_dispatcher := switch ? (io_to_regA.stream_from_dispatcher | io_to_regB.stream_from_dispatcher)
	io_to_regA.wr_addr_to_ramA := switch ? io_A.wr_addr_from_A | io_B.wr_addr_from_B
	io_to_regA.wr_data_to_ramA := switch ? io_A.wr_data_from_A | io_B.wr_data_from_B
	io_ramA.rd_addr_to_ramA := switch ? io_A.rd_addr_from_A | io_B.rd_addr_from_B
	io_ramA.rd_data_from_ramA := switch ? io_A.rd_data_to_A | io_B.rd_data_to_B

	io_ramB.wr_valid_to_ramB := switch ? io_B.wr_valid_from_B | io_A.wr_valid_from_A
	io_ramB.wr_addr_to_ramB := switch ? io_B.wr_addr_from_B | io_A.wr_addr_from_A
	io_ramB.wr_data_to_ramB := switch ? io_B.wr_data_from_B | io_A.wr_data_from_A
	io_ramB.rd_addr_to_ramB := switch ? io_B.rd_addr_from_B | io_A.rd_addr_from_A
	io_ramB.rd_data_from_ramB := switch ? io_B.rd_data_to_B | io_A.rd_data_to_A

}

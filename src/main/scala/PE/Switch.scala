package PE

import spinal.lib._

case class Switch (

	data_width: Int
    addr_width: Int
	
) extends Component {

	val io = new Bundle {
		val pe_done         = in Bool()
		val gather_pe_done  = in Bool()
		val switch_done  	= in Bool()
	}

	val io_pe = new Bundle {
		val wr_addr_from_pe = in Bits(addr_width)
		val wr_data_from_pe = in Bits(data_width)
		val rd_addr_from_pe = in Bits(addr_width)
		val rd_data_to_pe = out Bits(data_width)
	}

	val io_gather_pe = new Bundle {
		val wr_addr_from_gather_pe = in Bits(addr_width)
		val wr_data_from_gather_pe = in Bits(data_width)
		val rd_addr_from_gather_pe = in Bits(addr_width)
		val rd_data_to_gather_pe = out Bits(data_width)
	}

	val io_ramA = new Bundle {
		val wr_addr_to_ramA = out Bits(addr_width)
		val wr_data_to_ramA = out Bits(data_width)
		val rd_addr_to_ramA = out Bits(addr_width)
		val rd_data_from_ramA = in Bits(data_width)
	}

	val io_ramB = new Bundle {
		val wr_addr_to_ramB = out Bits(addr_width)
		val wr_data_to_ramB = out Bits(data_width)
		val rd_addr_to_ramB = out Bits(addr_width)
		val rd_data_from_ramB = in Bits(data_width)
	}

	val switch         	  = Reg(Bool()) init True
	val pe_done_r         = Reg(Bool()) init False
	val gather_pe_done_r  = Reg(Bool()) init False

	when(pe_done) {
		pe_done_r := True
	}.elsewhen(switch_done) {
		pe_done_r := False
	}

	when(pe_gather_done) {
		gather_pe_done_r := True
	} .elsewhen(switch_done) {
		gather_pe_done_r := False
	}

	when(pe_done_r && gather_pe_done_r) {
		switch := ~switch 
		switch_done = True
	} otherwise {
		switch_done = False
	}

	wr_addr_to_ramA = switch ? wr_addr_from_pe | wr_addr_from_gather_pe
	wr_data_to_ramA = switch ? wr_data_from_pe | wr_data_from_gather_pe
	rd_addr_to_ramA = switch ? rd_addr_from_pe | rd_addr_from_gather_pe
	rd_data_from_ramA = switch ? rd_data_to_pe | rd_data_to_gather_pe

	wr_addr_to_ramB = switch ? wr_addr_from_gather_pe | wr_addr_from_pe
	wr_data_to_ramB = switch ? wr_data_from_gather_pe | wr_data_from_pe
	rd_addr_to_ramB = switch ? rd_addr_from_gather_pe | rd_addr_from_pe
	rd_data_from_ramB = switch ? rd_data_to_gather_pe | rd_data_to_pe

}

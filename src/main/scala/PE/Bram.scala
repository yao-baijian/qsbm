package PE

import spinal.core._

case class Bram(

	bram_depth: Int = 64,
	bram_width: Int = 32,
	addr_width: Int = 6,

) extends Component{

	val io_wr = new Bundle{
		val wr_valid = in Bool()
		val wr_addr = in Bits(addr_width bits)
		val wr_data = in SInt(bram_width bits)
	}
	val io_rd = new Bundle{
		val rd_valid = in Bool()
		val rd_addr = in Bits(addr_width bits)
		val rd_data = out UInt(bram_width bits)
	}
	
	val bram = Mem(Bits(bram_width bits), bram_depth)
	bram.addAttribute("ram_style", "block")

	bram.write(
		enable = io.wr_valid,
		address = io.wr_addr,
		data = io.wr_data
	)

	io_rd.rd_data := bram.readSync(
		enable = io.rd_valid,
		address = io.rd_addr
	)

}

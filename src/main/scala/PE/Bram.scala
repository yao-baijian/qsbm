package PE

import spinal.core._

case class Bram(

	bram_depth: Int = 64,
	bram_width: Int = 16,
	addr_width: Int = 6

) extends Component{

	val io = new Bundle{
		val wr_valid = in Bool()
		val wr_addr = in UInt (addr_width bits)
		val wr_data = in Bits(bram_width bits)
		val rd_addr = in UInt (addr_width bits)
		val rd_data = out Bits (bram_width bits)
	}

	val bram = Mem(Bits(bram_width bits), bram_depth)
	bram.addAttribute("ram_style", "block")

	bram.write(
		enable = io.wr_valid,
		address = io.wr_addr,
		data = io.wr_data
	)

	io.rd_data := bram.readSync(
		enable = True,
		address = io.rd_addr
	)

}

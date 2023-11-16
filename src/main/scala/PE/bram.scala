package PE
import spinal.core._

case class Bram(
  bram_entry_num: Int = 64,
  bram_entry_type: Bits = Bits(32 bits)
) extends Component{

  val io = new Bundle{
    val wr_valid = in Bool()
    val wr_addr = in UInt(10 bits)
    val wr_data = in Bits(8 bits)
    val rd_valid = in Bool()
    val rd_addr = in UInt(10 bits)
  }

  val bram = Mem(bram_entry_type, bram_entry_num)
  bram.addAttribute("ram_style", "block")

  bram.write(
    enable = io.wr_valid,
    address = io.wr_addr,
    data = io.wr_data
  )

  bram.readSync(
    enable = io.rd_valid,
    address = io.rd_addr
  )

}

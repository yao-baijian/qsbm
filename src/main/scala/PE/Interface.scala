package PE
import spinal.core._
import spinal.lib._
class Interface(val config: PeConfig) extends Bundle with IMasterSlave {
//    val PADDR      = UInt(config.addressWidth bits)
//    val PSEL       = Bits(config.selWidth bits)
//    val PENABLE    = Bool()
//    val PREADY     = Bool()
//    val PWRITE     = Bool()
//    val PWDATA     = Bits(config.dataWidth bits)
//    val PRDATA     = Bits(config.dataWidth bits)
//    val PSLVERROR  = if(useSlaveError) Bool() else null   //This wire is created only when useSlaveError is true

    override def asMaster() : Unit = {
//        out(PADDR,PSEL,PENABLE,PWRITE,PWDATA)
//        in(PREADY,PRDATA)
//        if(useSlaveError) in(PSLVERROR)
    }
    //The asSlave is by default the flipped version of asMaster.
}
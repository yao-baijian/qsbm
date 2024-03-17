package Misc

import PE.PeConfig
import spinal.core._

import scala.language.postfixOps


case class PeBundle(config: PeConfig) extends Component {

    val io = new Bundle {
        val valid = Vec(out Bool(), config.thread_num)
    }

}


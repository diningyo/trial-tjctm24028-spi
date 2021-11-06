// See README.md for license details.

package ili9341.spi

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

object SpiAttr extends ChiselEnum {
  val Data, Cmd = Value
}

class SpiData extends Bundle {
  val attr = SpiAttr()
  val data = UInt(8.W)

  def set(d: UInt) = {
    data := d(7, 0)
    attr := SpiAttr(d(8))
  }
}

object SpiData {
  def apply(data: UInt, attr: SpiAttr.Type = SpiAttr.Data) = {
    val ret = new SpiData
    ret.data := data
    ret.attr := attr
    ret
  }
}

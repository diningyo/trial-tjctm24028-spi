// See README.md for license details.

package io

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

/**
  * SimpleIOクラスのパラメータ用クラス
  * @param addrBits アドレスのビット幅
  * @param dataBits データのビット幅
  */
case class SimpleIOParams
  (
   addrBits: Int = 4,
   dataBits: Int = 8
  )

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


/**
  * SimpleIO
  * @param p IOパラメータ
  */
class SimpleIO(p: SimpleIOParams) extends Bundle {
  val addr = Output(UInt(p.addrBits.W))
  val wren = Output(Bool())
  val rden = Output(Bool())
  val wrdata = Output(new SpiData)
  val rddv = Input(Bool())
  val rddata = Input(UInt(p.dataBits.W))

  override def cloneType: this.type =
    new SimpleIO(p).asInstanceOf[this.type]
}

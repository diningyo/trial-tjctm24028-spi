// See README.md for license details.

package ili9341

import chisel3._
import chisel3.stage._
import chisel3.util._

import io._
import spi._
import sequencer._

/**
  * ILI9341Controller
  * @param p SimpleIOのパラメータ
  * @param baudrate ボーレート
  * @param clockFreq クロック周波数(MHz)
  */
class ILI9341Controller(p: SimpleIOParams, baudrate: Int = 500000, clockFreq: Int = 100) extends Module {
  val io = IO(new SPIIO)

  val m_seq = Module(new InitSequencer(p))
  val m_spi = Module(new SPIController(baudrate, clockFreq))

  m_spi.io.mbus <> m_seq.io.sio
  io <> m_spi.io.spi
}

object genRTL extends App {
  val name = "ILI9341Controller"
  val p = SimpleIOParams()
  val rtl = (new ChiselStage).emitVerilog(
      new ILI9341Controller(p),
      Array(
        "-td=rtl", s"-o=$name"
      ))

  println(rtl)
}

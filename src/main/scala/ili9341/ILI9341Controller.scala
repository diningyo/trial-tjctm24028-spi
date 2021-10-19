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
class ILI9341Controller(baudrate: Int = 1000000, clockFreq: Int = 100) extends Module {
  val io = IO(new Bundle {
    val spi = new SPIIO
    val fill_button = Input(Bool())
    val init_done = Output(Bool())
  })

  val countMax = clockFreq * Math.pow(10, 6).toInt
  val button_ctr = Counter(countMax)
  val button_sync = button_ctr.value === (countMax - 1).U
  val r_fill_bottun = RegInit(false.B)

  when (button_sync) {
    r_fill_bottun := io.fill_button
  }

  val m_seq = Module(new MainSequencer())
  val m_spi = Module(new SPIController(baudrate, clockFreq))


  m_spi.io.mbus <> m_seq.io.sio
  m_seq.io.fill_button := r_fill_bottun
  io.init_done := m_seq.io.init_done
  io.spi <> m_spi.io.spi
}

object genRTL extends App {
  val name = "ILI9341Controller"
  val rtl = (new ChiselStage).emitVerilog(
      new ILI9341Controller(1000000),
      Array(
        "-td=rtl", s"-o=$name"
      ))

  println(rtl)
}

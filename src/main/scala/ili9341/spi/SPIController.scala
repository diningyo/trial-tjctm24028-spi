// See README.md for license details.

package ili9341.spi

import chisel3._
import chisel3.util._

import scala.math.{pow, round}

import io._

class SPIIO extends Bundle {
  val sck = Output(Bool())
  val led = Output(Bool())
  val dcx = Output(Bool())
  val csx = Output(Bool())
  val sdi = Output(UInt(1.W))
  val sdo = Input(UInt(1.W))
  val reset = Output(Bool())
  val debug_clk = Output(Bool())
}

class SPIControllerIO()
  (implicit debug: Boolean = false)extends Bundle {
  val mbus = Flipped(Decoupled(new SpiData))
  val spi= new SPIIO

  override def cloneType: this.type =
    new SPIControllerIO().asInstanceOf[this.type]
}

/**
  * SPIシーケンサーののトップモジュール
  * @param baudrate ボーレート
  * @param clockFreq クロックの周波数(MHz)
  */
class SPIController(baudrate: Int, clockFreq: Int) extends Module {

  val io = IO(new SPIControllerIO())

  val m_tx_fifo = Queue(io.mbus)
  val m_ctrl = Module(new TxRxCtrl(baudrate, clockFreq))

  m_tx_fifo <> m_ctrl.io.tx_data
  io.spi <> m_ctrl.io.spi
}

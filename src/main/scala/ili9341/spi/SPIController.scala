// See README.md for license details.

package chapter6.uart

import chisel3._
import chisel3.util._

import scala.math.{pow, round}

import io._

class SPIIO extends Bundle {
  val tx = Output(UInt(1.W))
  val rx = Input(UInt(1.W))
}

class SPIControllerIO(p: SimpleIOParams)
               (implicit debug: Boolean = false)extends Bundle {
  val mbus = Flipped(new SimpleIO(p))
  val uart= new SPIIO
  val dbg = if (debug) Some(new CSRDebugIO) else None

  override def cloneType: this.type =
    new SPIControllerIO(p).asInstanceOf[this.type]
}

/**
  * Uartのトップモジュール
  * @param baudrate ボーレート
  * @param clockFreq クロックの周波数(MHz)
  */
class SPIController(baudrate: Int, clockFreq: Int) extends Module {

  val p = SimpleIOParams()

  val io = IO(new SPIControllerIO(p))

  val m_reg = Module(new CSR(p))
  val m_ctrl = Module(new TxRxCtrl(baudrate, clockFreq))

  io.uart <> m_ctrl.io.uart

  io.mbus <> m_reg.io.sram
  m_reg.io.r2c <> m_ctrl.io.r2c
}

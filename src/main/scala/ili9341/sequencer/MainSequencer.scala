// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._

object State extends ChiselEnum {
  val sInit = Value
  val sIdle = Value
  val sFill = Value
  val sFinish = Value
}

class MainSequencer(p: SimpleIOParams)
  (implicit val debug: Boolean = false) extends Module {

  import State._
  import ili9341.spi.RegInfo._

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val fill_button = Input(Bool())
    val init_done = Output(Bool())
  })

  val m_init_seq = Module(new InitSequencer(p))
  val m_fill_seq = Module(new FillSequencer(p))

  // ステートマシン
  val r_stm = RegInit(State.sInit)
  val w_req_fill = RegInit(true.B) // FIXME

  when (r_stm === State.sInit) {
    when (m_init_seq.io.init_done) {
      r_stm := State.sIdle
    }
  }.elsewhen (r_stm === State.sIdle) {
    when (w_req_fill) {
      r_stm := State.sFill
    }
  }.elsewhen (r_stm === State.sFill) {
    when (m_fill_seq.io.fill_done) {
      r_stm := State.sIdle
    }
  }

  // IOの接続
  m_fill_seq.io.fill_button := io.fill_button
  m_init_seq.io.sio.ready := false.B
  m_fill_seq.io.sio.ready := false.B

  when (r_stm === State.sInit) {
    io.sio <> m_init_seq.io.sio
  }.otherwise {
    io.sio <> m_fill_seq.io.sio
  }

  io.init_done := m_init_seq.io.init_done
}

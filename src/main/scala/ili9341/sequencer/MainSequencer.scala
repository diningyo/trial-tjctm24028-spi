// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._

class MainSequencer() extends Module {

  object State extends ChiselEnum {
    val sInit = Value
    val sIdle = Value
    val sFill = Value
    val sFinish = Value
  }

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val fill_button = Input(Bool())
    val init_done = Output(Bool())
  })

  val m_init_seq = Module(new InitSequencer())
  val m_fill_seq = Module(new FillSequencer())

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
  io.sio.valid := false.B
  io.sio.bits.attr := SpiAttr.Data
  io.sio.bits.data := 0.U

  when (r_stm === State.sInit) {
    io.sio <> m_init_seq.io.sio
  }.elsewhen (r_stm === State.sFill) {
    io.sio <> m_fill_seq.io.sio
  }

  io.init_done := m_init_seq.io.init_done
}

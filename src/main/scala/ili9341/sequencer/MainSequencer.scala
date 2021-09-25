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
  })

  io.sio := DontCare

  val m_init_seq = Module(new InitSequencer(p))
  //val m_fill_seq = Module(new

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
    when (w_fill_done) {
      r_stm := State.sIdle
    }
  }


  // IOの接続
  val wrdata = Wire(new SpiData)
  wrdata := w_init_cmds(r_counter.value)
  io.sio.valid := r_stm =/= State.sIdle
  io.sio.bits := m_init_seq.io.sio.bits
}

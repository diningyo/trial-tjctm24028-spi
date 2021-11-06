// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import ili9341.spi._

object Color {
  val BLACK       = 0x0000 ///<   0,   0,   0
  val NAVY        = 0x000F ///<   0,   0, 123
  val DARKGREEN   = 0x03E0 ///<   0, 125,   0
  val DARKCYAN    = 0x03EF ///<   0, 125, 123
  val MAROON      = 0x7800 ///< 123,   0,   0
  val PURPLE      = 0x780F ///< 123,   0, 123
  val OLIVE       = 0x7BE0 ///< 123, 125,   0
  val LIGHTGREY   = 0xC618 ///< 198, 195, 198
  val DARKGREY    = 0x7BEF ///< 123, 125, 123
  val BLUE        = 0x001F ///<   0,   0, 255
  val GREEN       = 0x07E0 ///<   0, 255,   0
  val CYAN        = 0x07FF ///<   0, 255, 255
  val RED         = 0xF800 ///< 255,   0,   0
  val MAGENTA     = 0xF81F ///< 255,   0, 255
  val YELLOW      = 0xFFE0 ///< 255, 255,   0
  val WHITE       = 0xFFFF ///< 255, 255, 255
  val ORANGE      = 0xFD20 ///< 255, 165,   0
  val GREENYELLOW = 0xAFE5 ///< 173, 255,  41
  val PINK        = 0xFC18 ///< 255, 130, 198

  val table = Seq(BLACK, NAVY, DARKGREEN, DARKCYAN, MAROON, PURPLE,
                  OLIVE, LIGHTGREY, DARKGREY, BLUE, GREEN, CYAN, RED,
                  MAGENTA, YELLOW, WHITE, ORANGE, GREENYELLOW, PINK)
}

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

  val r_color_counter = Counter(Color.table.length)
  val color_table = WireDefault(VecInit(Color.table.map(_.U)))

  when (m_fill_seq.io.fill_done) {
    r_color_counter.inc
  }

  // IOの接続
  m_fill_seq.io.fill_button := io.fill_button
  m_fill_seq.io.color := color_table(r_color_counter.value)
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

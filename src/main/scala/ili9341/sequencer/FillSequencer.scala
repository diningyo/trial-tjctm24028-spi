// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._
import treadle.Command
import java.sql.Statement

/**
  * Sequencerのステート
  */
object FillState extends ChiselEnum {
  val sIDLE = Value
  val sCASET = Value
  val sPASET = Value
  val sRAMWR = Value
}

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


/**
  * 画面を塗りつぶすシーケンサー
  * @param p SimpleIOParamsのインスタンス
  * @param debug trueでデバッグポートが追加される
  */
class FillSequencer(p: SimpleIOParams)
  (implicit val debug: Boolean = false) extends Module {

  import State._
  import ili9341.spi.RegInfo._

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val fill_button = Input(Bool())
    val fill_done = Output(Bool())
  })

  io.sio := DontCare

  val m_stm = Module(new FillStateMachine)

  // ステートマシン
  val w_finish_fill = WireDefault(false.B)
  val color_table = WireDefault(VecInit(Color.table.map(_.U)))
  val r_color_counter = Counter(Color.table.length)
  val width = 240
  val height = 320

  // Fill
  val r_cmd_ctr = RegInit(0.U(10.W))
  val r_width_ctr = Counter(width)
  val r_height_ctr = Counter(height)
  val w_last_horizontal = r_width_ctr.value === (width - 1).U
  val w_last_vertical = r_height_ctr.value === (height - 1).U
  val w_done_cmd = r_cmd_ctr === 4.U
  val w_done_ramwr = r_cmd_ctr === 2.U
  val r_fill_stm = RegInit(FillState.sCASET)
  val w_running = r_fill_stm =/= FillState.sIDLE

  m_stm.io.done_caset := w_done_cmd && io.sio.fire()
  m_stm.io.done_paset := w_done_cmd && io.sio.fire()
  m_stm.io.done_ramwr := !r_cmd_ctr(0) && io.sio.fire()
  m_stm.io.is_last_vertical := w_last_vertical
  m_stm.io.is_last_horizontal := w_last_horizontal

  when (w_running && w_finish_fill) {
    r_color_counter.inc
  }

  when (w_running && m_stm.io.state.ramwr && (r_cmd_ctr >= 1.U) && io.sio.fire()) {
    when (!r_cmd_ctr(0)) {
      r_width_ctr.inc
    }
  }

  when ((m_stm.io.state.ramwr && w_last_horizontal && io.sio.fire() && !r_cmd_ctr(0))) {
    r_height_ctr.inc
  }

  w_finish_fill := w_last_horizontal && w_last_vertical && io.sio.fire() && !r_cmd_ctr(0)


  when (io.sio.fire()) {
    when ((m_stm.io.state.ramwr && w_last_horizontal && !r_cmd_ctr(0)) ||
      (!m_stm.io.state.ramwr && w_done_cmd)) {
      r_cmd_ctr := 0.U
    }.otherwise {
      r_cmd_ctr := r_cmd_ctr + 1.U
    }
  }

  val w_x_start = r_height_ctr.value
  val w_x_end = w_x_start + 1.U
  val w_y_start = r_width_ctr.value
  val w_y_end = w_y_start + 1.U

  // IOの接続
  val wrdata = Wire(new SpiData)

  when (m_stm.io.state.caset) {
    when (r_cmd_ctr === 0.U) {
      wrdata.set(Commands.ILI9341_CASET.U)
    }.otherwise {
      wrdata.attr := SpiAttr.Data
      when (r_cmd_ctr >= 3.U) {
        wrdata.data := (width - 1).U >> (r_cmd_ctr(0) << 3.U)
      }.otherwise {
        wrdata.data := 0.U
      }
    }
  }.elsewhen (m_stm.io.state.paset) {
    when (r_cmd_ctr === 0.U) {
      wrdata.set(Commands.ILI9341_PASET.U)
    }.otherwise {
      wrdata.attr := SpiAttr.Data
      wrdata.data := w_x_start >> (r_cmd_ctr(0) << 3.U)
    }
  }.otherwise {
    when (r_cmd_ctr === 0.U) {
      wrdata.set(Commands.ILI9341_RAMWR.U)
    }.otherwise {
      wrdata.attr := SpiAttr.Data
      wrdata.data := color_table(r_color_counter.value) >> (r_cmd_ctr(0) << 3.U)
    }
  }

  io.sio.valid := w_running
  io.sio.bits := wrdata
  io.fill_done := w_finish_fill
}

class FillStateMachine extends Module {
  val io = IO(new Bundle {
    val done_caset = Input(Bool())
    val done_paset = Input(Bool())
    val done_ramwr = Input(Bool())
    val is_last_horizontal = Input(Bool())
    val is_last_vertical = Input(Bool())
    val state = Output(new Bundle {
      val caset = Bool()
      val paset = Bool()
      val ramwr = Bool()
    })
  })

  val r_fill_stm = RegInit(FillState.sCASET)

  val w_is_caset = r_fill_stm === FillState.sCASET
  val w_is_paset = r_fill_stm === FillState.sPASET
  val w_is_ramwr = r_fill_stm === FillState.sRAMWR

  when (w_is_caset) {
    when (io.done_caset) {
      r_fill_stm := FillState.sPASET
    }
  }.elsewhen (w_is_paset) {
    when (io.done_paset) {
      r_fill_stm := FillState.sRAMWR
    }
  }.elsewhen (w_is_ramwr) {
    when (io.done_ramwr) {
      when (io.is_last_horizontal && io.is_last_vertical) {
        r_fill_stm := FillState.sCASET
      }.elsewhen (io.is_last_horizontal) {
        r_fill_stm := FillState.sPASET
      }
    }
  }

  io.state.caset := w_is_caset
  io.state.paset := w_is_paset
  io.state.ramwr := w_is_ramwr
}

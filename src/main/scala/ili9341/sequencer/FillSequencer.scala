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
  * Uartのデータをループバックするシーケンサー
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

  // ステートマシン
  val r_stm = RegInit(State.sInit)
  val r_counter = Counter(Init.initCmdSequence.length)
  val w_init_cmds = VecInit(Init.initCmdSequence.map(_.U))
  val w_finish_fill = WireDefault(false.B)

  when (r_stm === State.sInit && io.sio.ready) {
    r_counter.inc
  }

  when (r_stm === State.sInit) {
    when (r_counter.value === (Init.initCmdSequence.length - 1).U && io.sio.ready) {
      r_stm := State.sIdle
    }
  }.elsewhen (r_stm === State.sIdle) {
    r_stm := State.sFill
  }.elsewhen (r_stm === State.sFill) {
    when (w_finish_fill) {
      r_stm := State.sIdle
    }
  }

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

  when (r_stm === State.sFill && w_finish_fill) {
    r_color_counter.inc
  }

  when (r_stm === State.sFill && r_fill_stm === FillState.sRAMWR && (r_cmd_ctr >= 1.U) && io.sio.fire()) {
    when (!r_cmd_ctr(0)) {
      r_width_ctr.inc
    }
  }

  when (r_stm === State.sFill && (r_fill_stm === FillState.sRAMWR && w_last_horizontal && io.sio.fire() && !r_cmd_ctr(0))) {
    r_height_ctr.inc
  }

  w_finish_fill := w_last_horizontal && w_last_vertical && io.sio.fire() && !r_cmd_ctr(0)

  when (r_stm === State.sFill) {
    when (io.sio.fire()) {
      when ((r_fill_stm === FillState.sRAMWR && w_last_horizontal && !r_cmd_ctr(0)) ||
            (r_fill_stm =/= FillState.sRAMWR && w_done_cmd)) {
        r_cmd_ctr := 0.U
      }.otherwise {
        r_cmd_ctr := r_cmd_ctr + 1.U
      }
    }
  }

  when (r_fill_stm === FillState.sCASET) {
    when (w_done_cmd && io.sio.fire()) {
      r_fill_stm := FillState.sPASET
    }
  }.elsewhen (r_fill_stm === FillState.sPASET) {
    when (w_done_cmd && io.sio.fire()) {
      r_fill_stm := FillState.sRAMWR
    }
  }.elsewhen (r_fill_stm === FillState.sRAMWR) {
    when (w_last_horizontal && io.sio.fire() && !r_cmd_ctr(0)) {
      r_fill_stm := FillState.sPASET
    }
  }

  val w_x_start = r_height_ctr.value
  val w_x_end = w_x_start + 1.U
  val w_y_start = r_width_ctr.value
  val w_y_end = w_y_start + 1.U

  // IOの接続
  val wrdata = Wire(new SpiData)

  when (r_stm === State.sInit) {
    wrdata.set(w_init_cmds(r_counter.value))
  }.otherwise {
    when (r_fill_stm === FillState.sCASET) {
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
    }.elsewhen (r_fill_stm === FillState.sPASET) {
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
  }

  io.sio.valid := (r_stm === State.sInit) || (r_stm === State.sFill)
  io.sio.bits := wrdata
  io.fill_done := !(r_stm === State.sInit)
}

object genRTL extends App {
  val name = "InitSequencer"
  val p = SimpleIOParams()
  val rtl = (new ChiselStage).emitVerilog(
      new InitSequencer(p),
      Array(
        "-td=rtl", s"-o=$name"
      ))

  println(rtl)
}

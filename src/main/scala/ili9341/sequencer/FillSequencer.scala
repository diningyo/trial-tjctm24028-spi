// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import ili9341.spi._

object FillState extends ChiselEnum {
  val sIDLE = Value
  val sCASET = Value
  val sPASET = Value
  val sRAMWR = Value
}

/**
  * 画面を塗りつぶすシーケンサー
  */
class FillSequencer() extends Module {

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val color = Input(UInt(16.W))
    val fill_button = Input(Bool())
    val fill_done = Output(Bool())
  })

  val m_stm = Module(new FillStateMachine)

  // ステートマシン
  val w_finish_fill = WireDefault(false.B)
  val width = 240
  val height = 320

  // Fill
  val r_cmd_ctr = RegInit(0.U(10.W))
  val r_width_ctr = RegInit(0.U(log2Ceil(width).W))
  val r_height_ctr = Counter(height)
  val w_last_horizontal = r_width_ctr === (width - 1).U
  val w_last_vertical = r_height_ctr.value === (height - 1).U
  val w_last_cmd = r_cmd_ctr === 4.U
  val w_done_ramwr = r_cmd_ctr === 2.U
  val r_fill_stm = RegInit(FillState.sCASET)
  val w_running = r_fill_stm =/= FillState.sIDLE

  m_stm.io.start := io.fill_button
  m_stm.io.done_caset := w_last_cmd && io.sio.fire()
  m_stm.io.done_paset := w_last_cmd && io.sio.fire()
  m_stm.io.done_ramwr := !r_cmd_ctr(0) && io.sio.fire()
  m_stm.io.is_last_vertical := w_last_vertical
  m_stm.io.is_last_horizontal := w_last_horizontal

  when (w_running && m_stm.io.state.ramwr && (r_cmd_ctr >= 1.U) && io.sio.fire()) {
    when (!r_cmd_ctr(0)) {
      r_width_ctr := r_width_ctr + 1.U
    }
  }

  when ((m_stm.io.state.ramwr && w_last_horizontal && io.sio.fire() && !r_cmd_ctr(0))) {
    r_height_ctr.inc
  }

  w_finish_fill := w_last_horizontal && w_last_vertical && io.sio.fire() && !r_cmd_ctr(0)


  when (io.sio.fire()) {
    when ((m_stm.io.state.ramwr && w_last_horizontal && !r_cmd_ctr(0)) ||
      (!m_stm.io.state.ramwr && w_last_cmd)) {
      r_cmd_ctr := 0.U
    }.otherwise {
      r_cmd_ctr := r_cmd_ctr + 1.U
    }
  }

  val w_x_start = r_height_ctr.value
  val w_x_end = w_x_start + 1.U
  val w_y_start = r_width_ctr
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
      wrdata.data := io.color >> (r_cmd_ctr(0) << 3.U)
    }
  }

  io.sio.valid := w_running
  io.sio.bits := wrdata
  io.fill_done := w_finish_fill
}

class FillStateMachine extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
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

  val r_fill_stm = RegInit(FillState.sIDLE)

  val w_is_idle  = r_fill_stm === FillState.sIDLE
  val w_is_caset = r_fill_stm === FillState.sCASET
  val w_is_paset = r_fill_stm === FillState.sPASET
  val w_is_ramwr = r_fill_stm === FillState.sRAMWR

  when (w_is_idle) {
    when (io.start) {
      r_fill_stm := FillState.sCASET
    }
  }.elsewhen (w_is_caset) {
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
        r_fill_stm := FillState.sIDLE
      }.elsewhen (io.is_last_horizontal) {
        r_fill_stm := FillState.sPASET
      }
    }
  }

  io.state.caset := w_is_caset
  io.state.paset := w_is_paset
  io.state.ramwr := w_is_ramwr
}

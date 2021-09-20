// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._
import treadle.Command

/**
  * Sequencerのステート
  */
object State extends ChiselEnum {
  val sInit = Value
  val sIdle = Value
  val sFill = Value
  val sFinish = Value
}

object FillState extends ChiselEnum {
  val sCASET = Value
  val sPASET = Value
  val sRAMWR = Value
}

object Init {
  import Commands._

  val initCmdSequence = Seq(
  ILI9341_0xEF,     0x03, 0x80, 0x02,
  ILI9341_0xCF,     0x00, 0xC1, 0x30,
  ILI9341_0xED,     0x64, 0x03, 0x12, 0x81,
  ILI9341_0xE8,     0x85, 0x00, 0x78,
  ILI9341_0xCB,     0x39, 0x2C, 0x00, 0x34, 0x02,
  ILI9341_0xF7,     0x20,
  ILI9341_0xEA,     0x00, 0x00,
  ILI9341_PWCTR1,   0x23,             // Power control VRH[5:0]
  ILI9341_PWCTR2,   0x10,             // Power control SAP[2:0];BT[3:0]
  ILI9341_VMCTR1,   0x3e, 0x28,       // VCM control
  ILI9341_VMCTR2,   0x86,             // VCM control2
  ILI9341_MADCTL,   0x48,             // Memory Access Control
  ILI9341_VSCRSADD, 0x00,             // Vertical scroll zero
  ILI9341_PIXFMT,   0x55,
  ILI9341_FRMCTR1,  0x00, 0x18,
  ILI9341_DFUNCTR,  0x08, 0x82, 0x27, // Display Function Control
  ILI9341_0xF2,     0x00,                      // 3Gamma Function Disable
  ILI9341_GAMMASET, 0x01,             // Gamma curve selected
  ILI9341_GMCTRP1,  0x0F, 0x31, 0x2B, 0x0C, 0x0E, 0x08, 0x4E, 0xF1, 0x37, 0x07, 0x10, 0x03, 0x0E, 0x09, 0x00, // Set Gamma
  ILI9341_GMCTRN1,  0x00, 0x0E, 0x14, 0x03, 0x11, 0x07, 0x31, 0xC1, 0x48, 0x08, 0x0F, 0x0C, 0x31, 0x36, 0x0F, // Set Gamma
  ILI9341_SLPOUT,   0x80,                // Exit Sleep
  ILI9341_DISPON,   0x80                 // Display on
  )
}


/**
  * Uartのデータをループバックするシーケンサー
  * @param p SimpleIOParamsのインスタンス
  * @param debug trueでデバッグポートが追加される
  */
class InitSequencer(p: SimpleIOParams)
  (implicit val debug: Boolean = false) extends Module {

  import State._
  import ili9341.spi.RegInfo._

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val fill_button = Input(Bool())
    val init_done = Output(Bool())
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
    when (io.fill_button) {
      r_stm := State.sFill
    }
  }.elsewhen (r_stm === State.sFill) {
    when (w_finish_fill) {
      r_stm := State.sIdle
    }
  }

  val color = 0x0a0a
  val width = 240
  val height = 320

  // Fill
  val r_cmd_ctr = RegInit(0.U(3.W))
  val r_width_ctr = Counter(width)
  val r_height_ctr = Counter(height)
  val w_last_horizontal = r_height_ctr.value === (height - 1).U
  val w_last_vertical = r_width_ctr.value === (width - 1).U
  val w_done_cmd = r_cmd_ctr === 4.U
  val w_done_ramwr = r_cmd_ctr === 2.U
  val r_fill_stm = RegInit(FillState.sCASET)

  when (r_stm === State.sFill && (r_fill_stm === FillState.sRAMWR && w_done_ramwr && io.sio.fire())) {
    r_height_ctr.inc
    when (w_last_horizontal) {
      r_width_ctr.inc
    }
  }

  w_finish_fill := w_last_horizontal && w_last_vertical

  when (r_stm === State.sFill) {
    when (io.sio.fire()) {
      when ((r_fill_stm === FillState.sRAMWR && w_done_ramwr) ||
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
    when (w_done_ramwr && io.sio.fire()) {
      r_fill_stm := FillState.sCASET
    }
  }

  val w_x_start = r_width_ctr.value
  val w_x_end = w_x_start + 1.U
  val w_y_start = r_height_ctr.value
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
          wrdata.data := w_x_end >> (r_cmd_ctr(0) << 3.U)
        }.otherwise {
          wrdata.data := w_x_start >> (r_cmd_ctr(0) << 3.U)
        }
      }
    }.elsewhen (r_fill_stm === FillState.sPASET) {
      when (r_cmd_ctr === 0.U) {
        wrdata.set(Commands.ILI9341_PASET.U)
      }.otherwise {
        wrdata.attr := SpiAttr.Data
        when (r_cmd_ctr >= 3.U) {
          wrdata.data := w_y_end >> (r_cmd_ctr(0) << 3.U)
        }.otherwise {
          wrdata.data := w_y_start >> (r_cmd_ctr(0) << 3.U)
        }
      }
    }.otherwise {
      when (r_cmd_ctr === 0.U) {
        wrdata.set(Commands.ILI9341_RAMWR.U)
      }.otherwise {
        wrdata.attr := SpiAttr.Data
        wrdata.data := color.U >> (r_cmd_ctr(0) << 3.U)
      }
    }
  }

  io.sio.valid := (r_stm === State.sInit) || (r_stm === State.sFill)
  io.sio.bits := wrdata
  io.init_done := !(r_stm === State.sInit)
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

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
  * 初期化用のシーケンサー
  */
class InitSequencer() extends Module {

  object InitState extends ChiselEnum {
    val sRun = Value
    val sDone = Value
  }

  val io = IO(new Bundle {
    val sio = Decoupled(new SpiData)
    val init_done = Output(Bool())
  })

  // ステートマシン
  val r_stm = RegInit(InitState.sRun)
  val r_counter = Counter(Init.initCmdSequence.length)
  val w_init_cmds = VecInit(Init.initCmdSequence.map(_.U))
  val w_last_data = r_counter.value === (Init.initCmdSequence.length - 1).U

  when (r_stm === InitState.sRun) {
    when (w_last_data && io.sio.fire) {
      r_stm := InitState.sDone
    }
  }

  when (r_stm === InitState.sRun && io.sio.ready) {
    r_counter.inc
  }

  val r_init_done = RegInit(false.B)


  when (w_last_data && io.sio.fire) {
    r_init_done := true.B
  }

  // IOの接続
  val wrdata = Wire(new SpiData)

  wrdata.set(w_init_cmds(r_counter.value))
  io.sio.valid := (r_stm === InitState.sRun)
  io.sio.bits := wrdata
  io.init_done := r_init_done
}

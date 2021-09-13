// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.stage._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._

/**
  * Sequencerのステート
  */
object State extends ChiselEnum {
  val sInit = Value
  val sFinish = Value
}

object Init {
  import Commands._

  val initcmd = Seq(
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
    val sio = new SimpleIO(p)
  })

  io.sio := DontCare

  // ステートマシン
  val r_stm = RegInit(State.sInit)
  val r_counter = Counter(Init.initcmd.length)
  val w_init_cmds = VecInit(Init.initcmd.map(_.U))

  r_counter.inc

  // IOの接続
  val wrdata = Wire(new SpiData)
  wrdata.set(w_init_cmds(r_counter.value))
  io.sio.wren := r_stm === State.sInit
  io.sio.wrdata := wrdata
  io.sio.addr := txFifo.U
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

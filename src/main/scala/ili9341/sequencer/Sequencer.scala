// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import io._

/**
  * Sequencerのステート
  */
object State extends ChiselEnum {
  val sIdle = Value
  val sRX = Value
  val sTX = Value
}


object Init {
  import Commands._

  val initcmd = Seq(
  0xEF, 3, 0x03, 0x80, 0x02,
  0xCF, 3, 0x00, 0xC1, 0x30,
  0xED, 4, 0x64, 0x03, 0x12, 0x81,
  0xE8, 3, 0x85, 0x00, 0x78,
  0xCB, 5, 0x39, 0x2C, 0x00, 0x34, 0x02,
  0xF7, 1, 0x20,
  0xEA, 2, 0x00, 0x00,
  ILI9341_PWCTR1  , 1, 0x23,             // Power control VRH[5:0]
  ILI9341_PWCTR2  , 1, 0x10,             // Power control SAP[2:0];BT[3:0]
  ILI9341_VMCTR1  , 2, 0x3e, 0x28,       // VCM control
  ILI9341_VMCTR2  , 1, 0x86,             // VCM control2
  ILI9341_MADCTL  , 1, 0x48,             // Memory Access Control
  ILI9341_VSCRSADD, 1, 0x00,             // Vertical scroll zero
  ILI9341_PIXFMT  , 1, 0x55,
  ILI9341_FRMCTR1 , 2, 0x00, 0x18,
  ILI9341_DFUNCTR , 3, 0x08, 0x82, 0x27, // Display Function Control
  0xF2, 1, 0x00,                         // 3Gamma Function Disable
  ILI9341_GAMMASET , 1, 0x01,             // Gamma curve selected
  ILI9341_GMCTRP1 , 15, 0x0F, 0x31, 0x2B, 0x0C, 0x0E, 0x08, // Set Gamma
    0x4E, 0xF1, 0x37, 0x07, 0x10, 0x03, 0x0E, 0x09, 0x00,
  ILI9341_GMCTRN1 , 15, 0x00, 0x0E, 0x14, 0x03, 0x11, 0x07, // Set Gamma
    0x31, 0xC1, 0x48, 0x08, 0x0F, 0x0C, 0x31, 0x36, 0x0F,
  ILI9341_SLPOUT  , 0x80,                // Exit Sleep
  ILI9341_DISPON  , 0x80,                // Display on
    0x00                                   // End of list
  )
}


/**
  * Uartのデータをループバックするシーケンサー
  * @param p SimpleIOParamsのインスタンス
  * @param debug trueでデバッグポートが追加される
  */
class Sequencer(p: SimpleIOParams)
               (implicit val debug: Boolean = false) extends Module {

  import State._
  import ili9341.spi.RegInfo._

  val io = IO(new Bundle {
    val sio = new SimpleIO(p)
    val debug_stm = if (debug) Some(Output(State())) else None
  })

  // ステートマシン
  val r_stm = RegInit(State.sTX)

  // sIdleステートの制御
  val w_has_rx_data = Wire(Bool())
  val r_read_interval = RegInit(0.U(4.W))
  val w_read_req = r_read_interval === 0xf.U

  when (r_stm === sIdle) {
    r_read_interval := r_read_interval + 1.U
  } .otherwise {
    r_read_interval := 0.U
  }

  w_has_rx_data := (r_stm === sIdle) && io.sio.rddv && io.sio.rddata(0)

  // sRXステートの制御
  val r_rx_data = Reg(UInt(8.W))
  val r_rx_fifo_req = RegNext(w_has_rx_data, false.B)
  val w_done_rx_data = (r_stm === sRX) && io.sio.rddv

  // sTXステートの制御
  val r_wait_data = RegInit(false.B)
  val r_fifo_full = RegInit(false.B)
  val w_done_tx_data = Wire(Bool())
  val w_tx_state_addr = Mux(r_fifo_full, stat.U, txFifo.U)
  val w_tx_state_rden = r_fifo_full && !r_wait_data && (r_stm === sTX)
  val w_tx_state_wren = !r_fifo_full && !r_wait_data && (r_stm === sTX)

  when (w_done_rx_data) {
    r_rx_data := io.sio.rddata
  }

  // ステート遷移時にr_fifo_fullフラグをセット
  when (w_done_rx_data) {
    r_fifo_full := true.B
  } .elsewhen(r_stm === sTX) {
    when (io.sio.rddv) {
      r_fifo_full := io.sio.rddata(3)
    }
  } .otherwise {
    r_fifo_full := false.B
  }

  // sTXステート内の処理切り替え
  when (r_stm === sTX) {
    when (io.sio.rddv) {
      r_wait_data := false.B
    } .elsewhen(w_done_tx_data) {
      r_wait_data := false.B
    } .elsewhen(!r_wait_data) {
      r_wait_data := true.B
    }
  } .otherwise {
    r_wait_data := false.B
  }

  w_done_tx_data := w_tx_state_wren

  // ステートマシン
  switch (r_stm) {
    is (sIdle) {
      r_stm := r_stm
    }

    is (sTX) {
      when (w_done_tx_data) {
        r_stm := sIdle
      }
    }
  }

  // IOの接続
  io.sio.wren := w_tx_state_wren
  io.sio.wrdata := Commands.ILI9341_RDMODE.U
  io.sio.rden := w_read_req || r_rx_fifo_req || w_tx_state_rden
  io.sio.addr := MuxCase(stat.U, Seq(
    (r_stm === sRX) -> rxFifo.U,
    (r_stm === sTX) -> w_tx_state_addr
  ))

  // テスト用にステートマシンの値を出力
  if (debug) {
    io.debug_stm.get := r_stm
  }
}

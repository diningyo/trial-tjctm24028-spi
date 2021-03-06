// See README.md for license details.

package ili9341.spi

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

import scala.math.{pow, round}

/**
  * SPIの方向
  */
sealed trait SPIDirection
case object SPITx extends SPIDirection
case object SPIRx extends SPIDirection

/**
  * SPIの制御モジュール
  * @param spiFreq SPIの周波数(MHz)
  * @param clockFreq クロックの周波数(MHz)
  */
class TxRxCtrl(spiFreq: Int=9600,
               clockFreq: Int=100) extends Module {
  val io = IO(new Bundle {
    val spi = new SPIIO
    val tx_data = Flipped(Decoupled(new SpiData))
  })

  val durationCount = round(clockFreq / spiFreq * pow(10, 6)).toInt

  println(s"durationCount = ${durationCount}")

  val m_tx_ctrl = Module(new Ctrl(SPITx, durationCount))

  io.spi.debug_clk := false.B

  val dcx = RegInit(true.B)

  io.spi.sck := m_tx_ctrl.io.sck
  io.spi.dcx := !io.tx_data.bits.attr.asUInt()
  io.spi.led := true.B

  val r_reset = RegInit(false.B)

  r_reset := true.B
  io.spi.reset := true.B //r_reset

  io.spi.sdi := m_tx_ctrl.io.spi
  io.spi.csx := m_tx_ctrl.io.csx
  m_tx_ctrl.io.reg <> io.tx_data
}

/**
  * SPIの各方向の制御モジュール
  * @param direction SPIの送受信の方向
  * @param durationCount 1bit分のカウント
  */
class Ctrl(direction: SPIDirection, durationCount: Int) extends Module {
  val io = IO(new Bundle {
    val spi = direction match {
      case SPITx => Output(UInt(1.W))
      case SPIRx => Input(UInt(1.W))
    }
    val reg = direction match {
      case SPITx => Flipped(Decoupled(new SpiData))
      case SPIRx => Flipped(Decoupled(new SpiData))
    }
    val csx = Output(Bool())
    val sck = Output(Bool())
  })

  val m_stm = Module(new CtrlStateMachine)
  val r_duration_ctr = RegInit(durationCount.U)
  val r_bit_idx = RegInit(0.U(3.W))

  // 受信方向は受信した信号と半周期ずれたところで
  // データを確定させるため初期値をずらす
  val initDurationCount = direction match {
    case SPITx => 0
    case SPIRx => durationCount / 2
  }

  io.csx := !(m_stm.io.state.data)

  val r_sck = RegInit(false.B)

  when (m_stm.io.state.data) {
    when (r_duration_ctr === (durationCount - 1).U || r_duration_ctr === (durationCount / 2).U) {
      r_sck := !r_sck
    }
  }

  io.sck := r_sck

  // 動作開始のトリガはTx/Rxで異なるため
  // directionをmatch式で処理
  val w_start_req = direction match {
    case SPITx => io.reg.asInstanceOf[DecoupledIO[SpiData]].valid
    case SPIRx => !io.spi
  }

  val w_update_req = Mux(m_stm.io.state.data,
    r_duration_ctr === (durationCount - 1).U,
    r_duration_ctr === ((durationCount / 2) - 1).U)

  // アイドル時の制御
  when (m_stm.io.state.idle) {
    when (w_start_req) {
      r_duration_ctr := initDurationCount.U
    } .otherwise {
      r_duration_ctr := 0.U
    }
  } .otherwise {
    when (!w_update_req) {
      r_duration_ctr := r_duration_ctr + 1.U
    } .otherwise {
      r_duration_ctr := 0.U
    }
  }

  // データ処理時の制御
  when (m_stm.io.state.data) {
    when (w_update_req) {
      r_bit_idx := r_bit_idx + 1.U
    }
  } .otherwise {
    r_bit_idx := 0.U
  }

  // クラスパラメータのdirectionを使って各方向の論理を実装
  direction match {
    case SPITx =>
      val reg = io.reg.asInstanceOf[DecoupledIO[SpiData]]

      io.spi := reg.bits.data(7.U - r_bit_idx)
      reg.ready := m_stm.io.state.stop

    case SPIRx =>
      val reg = io.reg.asInstanceOf[DecoupledIO[SpiData]]
      val r_rx_data = RegInit(0.U)

      when (m_stm.io.state.idle && w_start_req) {
        r_rx_data := 0.U
      } .elsewhen (m_stm.io.state.data) {
        when (w_update_req) {
          r_rx_data := r_rx_data | (io.spi << r_bit_idx)
        }
      }
      reg.ready := m_stm.io.state.stop
      reg.bits.attr := SpiAttr.Data
      reg.bits.data := r_rx_data
  }

  // m_stm <-> ctrlの接続
  m_stm.io.start_req := w_start_req
  m_stm.io.stop_req := m_stm.io.state.data && w_update_req && (r_bit_idx === 7.U)
  m_stm.io.fin := m_stm.io.state.stop
}

/**
  * SPI制御モジュールのステートマシン
  */
class CtrlStateMachine extends Module {

  val io = IO(new Bundle {
    val start_req = Input(Bool())
    val stop_req = Input(Bool())
    val fin = Input(Bool())

    // ステートの出力
    val state = Output(new Bundle {
      val idle = Output(Bool())
      val data = Output(Bool())
      val stop = Output(Bool())
    })
  })

  // ステート用のEnum
  object CtrlState extends ChiselEnum {
    val sIdle = Value
    val sData = Value
    val sStop = Value
  }

  val r_stm = RegInit(CtrlState.sIdle)

  // ステートマシンの実装
  switch (r_stm) {
    is (CtrlState.sIdle) {
      when (io.start_req) {
        r_stm := CtrlState.sData
      }
    }

    is (CtrlState.sData) {
      when (io.stop_req) {
        r_stm := CtrlState.sStop
      }
    }

    is (CtrlState.sStop) {
      when (io.fin) {
        r_stm := CtrlState.sIdle
      }
    }
  }

  // output
  io.state.idle := r_stm === CtrlState.sIdle
  io.state.data := r_stm === CtrlState.sData
  io.state.stop := r_stm === CtrlState.sStop
}

// See README.md for license details.

package ili9341.spi

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.util._

import scala.math.{pow, round}

import io._

/**
  * SPIの方向
  */
sealed trait SPIDirection
case object SPITx extends SPIDirection
case object SPIRx extends SPIDirection

/**
  * SPIの制御モジュール
  * @param baudrate ボーレート
  * @param clockFreq クロックの周波数(MHz)
  */
class TxRxCtrl(baudrate: Int=9600,
               clockFreq: Int=100) extends Module {
  val io = IO(new Bundle {
    val spi = new SPIIO
    val tx_data = Flipped(Decoupled(new SpiData))
  })

  val durationCount = round(clockFreq * pow(10, 6) / baudrate).toInt

  println(s"durationCount = ${durationCount}")

  val m_tx_ctrl = Module(new Ctrl(SPITx, durationCount))
  //val m_rx_ctrl = Module(new Ctrl(SPIRx, durationCount))

  val r_sck_ctr = RegInit(0.U(32.W))

  when (r_sck_ctr === (durationCount / 2).U) {
    r_sck_ctr := 0.U
  }.otherwise {
    r_sck_ctr := r_sck_ctr + 1.U
  }

  val r_sck = RegInit(true.B)

  when (r_sck_ctr === (durationCount / 2).U) {
    r_sck := !r_sck
  }

  val r_debug_clk = RegInit(0.U(32.W))

  when (r_debug_clk === (durationCount / 2).U) {
    r_debug_clk := 0.U
  }.otherwise {
    r_debug_clk := r_debug_clk + 1.U
  }

  io.spi.debug_clk := r_debug_clk === (durationCount / 2).U

  val dcx = RegInit(true.B)

  //io.spi.sck := Mux(!(m_tx_ctrl.io.csx && m_rx_ctrl.io.csx),  r_sck, false.B)
  io.spi.sck := Mux(!m_tx_ctrl.io.csx, r_sck, false.B)
  io.spi.dcx := !io.tx_data.bits.attr.asUInt()//true.B  // tmp. send command only
  io.spi.led := true.B

  val r_reset = RegInit(false.B)

  r_reset := true.B
  io.spi.reset := true.B //r_reset

  io.spi.sdi := m_tx_ctrl.io.spi
  //io.spi.csx := m_tx_ctrl.io.csx && m_rx_ctrl.io.csx
  io.spi.csx := m_tx_ctrl.io.csx
  m_tx_ctrl.io.reg <> io.tx_data

  //m_rx_ctrl.io.spi := io.spi.sdo
  //m_rx_ctrl.io.reg <> io.r2c.rx
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
  })

  val m_stm = Module(new CtrlStateMachine)
  val r_duration_ctr = RegInit(durationCount.U)
  val r_bit_idx = RegInit(0.U(3.W))

  // 受信方向は受信した信号と半周期ずれたところで
  // データを確定させるため初期値をずらす
  val initDurationCount = direction match {
    case SPITx => durationCount / 4
    case SPIRx => durationCount / 2
  }

  io.csx := !m_stm.io.state.data

  // 動作開始のトリガはTx/Rxで異なるため
  // directionをmatch式で処理
  val w_start_req = direction match {
    case SPITx => io.reg.asInstanceOf[DecoupledIO[SpiData]].valid
    case SPIRx => !io.spi
  }

  val w_update_req = r_duration_ctr === (durationCount - 1).U
  val w_fin = m_stm.io.state.stop && w_update_req

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

      io.spi := MuxCase(1.U, Seq(
        m_stm.io.state.start -> 0.U,
        m_stm.io.state.data -> reg.bits.data(7.U - r_bit_idx)
      ))

      reg.ready := m_stm.io.state.stop && w_update_req

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
      reg.ready := w_fin
      reg.bits.attr := SpiAttr.Data
      reg.bits.data := r_rx_data
  }

  // m_stm <-> ctrlの接続
  m_stm.io.start_req := w_start_req
  m_stm.io.data_req := m_stm.io.state.start && w_update_req
  m_stm.io.stop_req := m_stm.io.state.data && w_update_req && (r_bit_idx === 7.U)
  m_stm.io.fin := w_fin
}

/**
  * SPI制御モジュールのステートマシン
  */
class CtrlStateMachine extends Module {

  val io = IO(new Bundle {
    val start_req = Input(Bool())
    val data_req = Input(Bool())
    val stop_req = Input(Bool())
    val fin = Input(Bool())

    // ステートの出力
    val state = Output(new Bundle {
      val idle = Output(Bool())
      val start = Output(Bool())
      val data = Output(Bool())
      val stop = Output(Bool())
    })
  })

  // ステート用のEnum
  object CtrlState extends ChiselEnum {
    val sIdle = Value
    val sStart = Value
    val sData = Value
    val sStop = Value
  }

  val r_stm = RegInit(CtrlState.sIdle)

  // ステートマシンの実装
  switch (r_stm) {
    is (CtrlState.sIdle) {
      when (io.start_req) {
        r_stm := CtrlState.sStart
      }
    }

    is (CtrlState.sStart) {
      when (io.data_req) {
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
  io.state.start := r_stm === CtrlState.sStart
  io.state.data := r_stm === CtrlState.sData
  io.state.stop := r_stm === CtrlState.sStop
}

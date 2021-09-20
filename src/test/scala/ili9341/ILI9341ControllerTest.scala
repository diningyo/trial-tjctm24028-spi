// See README.md for license details.

package ili9341

import chisel3._
import chisel3.util.experimental.BoringUtils

import org.scalatest._
import chiseltest._
import chiseltest.internal.VerilatorBackendAnnotation
import chiseltest.experimental.TestOptionBuilder._

import io._
import spi._
import sequencer._

class ILI9341ControllerTestTb(p: SimpleIOParams, baudrate: Int = 500000, clockFreq: Int = 100) extends Module {

  val io = IO(new Bundle {
    val spi = new SPIIO
    val fill_button = Input(Bool())
    val init_done = Output(Bool())
  })

  io := DontCare

  val dut_ctrl = Module(new ILI9341Controller(p, baudrate))

  io <> dut_ctrl.io
}

class ILI9341ControllerTest extends FlatSpec with ChiselScalatestTester with Matchers with ParallelTestExecution {

  val annos = Seq(VerilatorBackendAnnotation)
  val p = new SimpleIOParams

  behavior of "ILI9341Controller"

  it should f"be passed init test" in {
    test(new ILI9341ControllerTestTb(p, 20000000)).withAnnotations(annos) { c =>
      c.clock.setTimeout(10000000)

      println(s"${c.io.init_done.peek.litToBoolean}")
      println(s"init_done = ${c.io.init_done.peek().litValue()}")
      while (!c.io.init_done.peek.litToBoolean) {
        c.clock.step(1)
      }

      println(s"${c.io.init_done.peek.litToBoolean}")
      println(s"init_done = ${c.io.init_done.peek().litValue()}")

      println("init done!!")
      c.clock.step(1000)

      c.io.fill_button.poke(true.B)
      c.clock.step(1000000)
    }
  }
}

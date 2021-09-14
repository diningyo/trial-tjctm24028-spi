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

class ILI9341ControllerTestTb(p: SimpleIOParams) extends Module {

  val io = IO(new SPIIO)

  io := DontCare

  val dut_ctrl = Module(new ILI9341Controller(p))

  io <> dut_ctrl.io
}

class ILI9341ControllerTest extends FlatSpec with ChiselScalatestTester with Matchers with ParallelTestExecution {

  val annos = Seq(VerilatorBackendAnnotation)
  val p = new SimpleIOParams

  behavior of "ILI9341Controller"

  it should f"be passed init test" in {
    test(new ILI9341ControllerTestTb(p)).withAnnotations(annos) { c =>
      c.clock.setTimeout(50000)
      c.clock.step(10000)
    }
  }
}

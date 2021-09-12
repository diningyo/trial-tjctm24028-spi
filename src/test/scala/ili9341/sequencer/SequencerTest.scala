// See README.md for license details.

package ili9341.sequencer

import chisel3._
import chisel3.util.experimental.BoringUtils

import org.scalatest._
import chiseltest._
import chiseltest.internal.VerilatorBackendAnnotation
import chiseltest.experimental.TestOptionBuilder._
import io._

class SequencerTestTb(p: SimpleIOParams) extends Module {

  val io = IO(new Bundle {
    val sio = new SimpleIO(p)
  })

  io := DontCare

  val dut_seq = Module(new Sequencer(p))

  dut_seq.io.sio <> io.sio
}

class CpuTest extends FlatSpec with ChiselScalatestTester with Matchers with ParallelTestExecution {

  val annos = Seq(VerilatorBackendAnnotation)
  val p = new SimpleIOParams

  behavior of "Sequencer"

  it should f"be passed init test" in {
    test(new SequencerTestTb(p)).withAnnotations(annos) { c =>
      c.clock.setTimeout(5000)
      c.clock.step(1000)
    }
  }
}

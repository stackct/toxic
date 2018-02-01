package process

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import java.nio.file.*
import java.nio.file.attribute.*

import util.*

class ProcessRunningStdInTest {
  def emulator

  @Before
  void before() {
    def emulatorDir = new File(Resource.path("${this.class.name.replaceAll(/\./, "/")}.class")).parent
    emulator = "${emulatorDir}/../../../test/unit/process/ProcessRunningStdInTestEmualator.sh"

    Files.setPosixFilePermissions(Paths.get(emulator), [
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_READ,,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OTHERS_EXECUTE
    ] as Set)
  }

  @Ignore
  @Test
  void should_capture_all_output_from_background_process() {
    def iterations = 1000
    def seconds = 0

    def output = new ProcessOutputString()

    def running = ProcessRunning.factory([emulator, iterations, seconds], output)
    running.waitForever()

    assert iterations == output.output.split("@").size()
  }

  @Ignore
  @Test
  void should_loose_output_if_client_dies_first() {
    def iterations = 10
    def seconds = 1

    def interrupted = false

    def output = new ProcessOutputString()

    def client = Thread.start() {
      def running = ProcessRunning.factory([emulator, iterations, seconds], output)

      try {
        running.waitFor(1000) // Milli-seconds
      }
      catch (InterruptedException e) {
        // This is expected to happen
        interrupted = true
      }
    }

    client.join(5000)

    assert interrupted, "should have quit before completed"
    assert iterations >= output.output.split("@").size()
  }

  /*
   * Waiting for process to end and then complete reading input stream from child process.
   *
   * Threads:
   * main     - process.waitFor
   * Thread-1 - inputStream.read(bytes)
   *
   * Time -->
   * main:      process.start()                                  waitForever()
   * Thread-1:    consumeProcessOutput  inputStream.write()
   * Thread-2:    consumeProcessOutput  inputStream.read()       
   */
  @Ignore
  @Test
  void should_ensure_that_all_process_output_is_read_after_process_stops() {
    def iterations = 1
    def seconds = 1
    def padding = 1000

    def output = new ProcessOutputString()
    
    def running = ProcessRunning.factory([emulator, iterations, seconds, padding], output)
    running.waitForever()

    assert iterations >= output.output.split("@").size()
  }
}


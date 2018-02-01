package process

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import java.lang.management.ManagementFactory

class ProcessTest {

  @After
  void after() {
    String.metaClass = null
  }

  @Test
  void should_mock_a_process() {
    Process.mock() {
      Process.command("ANYTHING").start() == 0
    }
  }

  @Test(expected = org.codehaus.groovy.runtime.powerassert.PowerAssertionError)
  void should_mock_a_process_and_verify() {
    Process.mock() {
      // Do not start a process which will make the mocks fail.
    }
  }

  @Test
  void should_start_with_simple_api() {
    Process.mock(output: "abc") { 
      assert Process.start("echo abc").output == "abc" 
    }
  }

  @Test
  void should_start_with_and_wait_forever_for_it_to_finish() {
    Process.mock(output: "abc", expectedPause: 1000, expectedTimeUnit: java.util.concurrent.TimeUnit.DAYS) { 
      assert Process.start("echo abc").output == "abc" 
    }
  }

  @Test
  void should_start_with_and_wait_for_a_specific_time() {
    Process.mock(output: "abc", expectedPause: 23, expectedTimeUnit: java.util.concurrent.TimeUnit.HOURS) { 
      def process = Process.start("echo abc", [
          timeout: 23,
          timeoutUnit: java.util.concurrent.TimeUnit.HOURS
      ])

      assert process.output == "abc" 
    }
  }

  @Test
  void should_construct_a_process_with_and_wait_for_a_specific_time() {
    Process.mock(output: "abc", expectedPause: 10, expectedTimeUnit: java.util.concurrent.TimeUnit.SECONDS) { 
      def process = Process.command("echo abc")
      process.timeout = 10
      process.timeoutUnit = java.util.concurrent.TimeUnit.SECONDS

      process.start()
      assert process.output == "abc" 
    }
  }

  @Test
  void should_start_with_simple_api_with_list() {
    Process.mock(output: "abc") { 
      assert Process.start(["echo", "abc"]).output == "abc" 
    }
  }

  @Test
  void should_start_with_simple_api_set_working_dir() {
    Process.mock(output: "abc", directory: { dir -> assert dir.path == "/some/dir" }) { 
      assert Process.start("echo abc", [currentDir: "/some/dir"]).output == "abc" 
    }
  }

  @Test
  void should_run_command() {
    Process.mock(output: "Hello World", error: "An Error", exitCode: 123) {
      def process = Process.command("ls -la")
      def exitCode = process.start()

      assert process.output == "Hello WorldAn Error"
      assert exitCode == 123
    }
  }

  @Test
  void should_run_command_that_does_not_exist() {
    Process.mock(exception: new IOException("oops") ) {
      def process = Process.command("ls -la")
      def exitCode = process.start()

      assert process.output == "ls: command not found"
      assert exitCode == 1
    }
  }

  @Test
  void should_run_command_with_custom_output_type() {
    Process.mock(output: "Hello World", error: "An Error") {
      def process = Process.command("ls -la")
      process.output = new SimulatedProcessOutput()
      def exitCode = process.start()

      assert process.output == "simulated output"
    }
  }

  @Test
  void should_run_command_with_file_output() {
    def writer = new StringWriter()

    def fileMock = new MockFor(File)
    fileMock.ignore.exists { false }
    fileMock.ignore.newWriter { writer }
    fileMock.ignore.leftShift { string -> writer << string }
    fileMock.ignore.getText { writer.toString() }
    fileMock.ignore.asBoolean() { true }
    fileMock.ignore.flush { writer.flush() }

    Process.mock(output: "Hello World", error: "An Error") {
      fileMock.use {

        def process = Process.command("ls -la")
        process.output = new ProcessOutputFile("/tmp/process_output_file")
        def exitCode = process.start()

        assert writer.toString() == "Hello WorldAn Error"
        assert process.output == "Hello WorldAn Error"
      }
    }
  }

  @Test
  void should_failed_run() {
    Process.mock(error: 'Cannot run program "UNKNOWN_COMMAND": error=2, No such file or directory', exitCode: 1) {
      def process = Process.command("UNKNOWN_COMMAND")
      def exitCode = process.start()

      assert 'Cannot run program "UNKNOWN_COMMAND": error=2, No such file or directory' == process.output
    }
  }

  @Test
  void should_format_process_as_string() {
    assert Process.command("a b c").toString() == "a b c"
  }

  // Runtime MXBean returns "40860@C02JV0UGDKQ5.local"
  @Test
  void should_current_process_pid() {
    assert Process.pid() ==~ /[0-9]+/
  }

  @Test
  void should_defualt_to_pid_0() {
    checkPid(expectedPid: "40860", runtimeName: "40860@hostname")
    checkPid(expectedPid: "0",     runtimeName: "hostname")
    checkPid(expectedPid: "0",     runtimeName: "")
    checkPid(expectedPid: "0",     runtimeName: null)
  }

  private checkPid(options) {
    def beanMock = new MockFor(ManagementFactory)
    beanMock.ignore.getRuntimeMXBean() {
      [name: options.runtimeName ]
    }

    beanMock.use {
      assert Process.pid() == options.expectedPid
    }
  }

  @Test
  void should_find_root_dir() {
    assert Process.rootDir().size() > 0
    assert Process.rootDir("..").size() > 0
  }

  @Test
  void should_command_runs_from_current_directory() {
    Process.mock(directory: { dir -> assert dir.path == "." }) {
      def process = Process.command("pwd")
      def exitCode = process.start()

      assert exitCode == 0
    }
  }

  @Test
  void should_command_runs_from_specific_directory() {
    Process.mock(directory: { dir -> assert dir.path == "/some/dir" }) {
      def process = Process.command("pwd", "/some/dir")
      def exitCode = process.start()

      assert exitCode == 0
    }
  }
}

class SimulatedProcessOutput extends ProcessOutput {
  def process
  def builder

  def writeLine(text) { println text }
  def getProcess() { this.process }
  def setProcess(process) { this.process = process }
  def setBuilder(builder) { this.builder = builder }

  def getOutput() { "simulated output" }
  def getStream() { "simulated output" }
}


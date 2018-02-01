package process

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import log.*

class ProcessRunningTest {
  def originalLevel

  @Before
  void before() {
    originalLevel = ProcessRunning.log.level
  }

  @After
  void after() {
    ProcessRunning.log.level = originalLevel
  }

  @Test
  void should_start_from_string() {
    Process.mock(output: "HelloWorld") {
      def output = new ProcessOutputString()
      def process = ProcessRunning.factory("echo HelloWorld", output)
      process.waitForever()

      assert process.output == "HelloWorld"
    }
  }

  @Test
  void should_start_from_list() {
    Process.mock(output: "HelloWorld") {
      def output = new ProcessOutputString()
      def process = ProcessRunning.factory(["echo", "the output"], output)
      process.waitForever()

      assert process.output == "HelloWorld"
    }
  }

  @Test
  void should_start_command_that_does_not_exist() {
    Process.mock(exception: new IOException("oops")) {
      def output = new ProcessOutputString()
      def process = ProcessRunning.factory("INVALID COMMAND", output)

      assert process.output == "INVALID: command not found"
    }
  }
  
  @Test
  void should_start_command_in_current_dir() {
    def output = new ProcessOutputString()

    Process.mock(directory: { dir -> assert dir.path == "." }) {
      ProcessRunning.factory("pwd", output)
    }
  }
  
  @Test
  void should_start_command_in_specific_dir() {
    def output = new ProcessOutputString()

    Process.mock(directory: { dir -> assert dir.path == "/some/dir" }) {
      ProcessRunning.factory("pwd", output, "/some/dir")
    }
  }

  @Test
  void should_wait_for_a_process_with_a_timeout_that_has_not_expired() {
    def running = ProcessRunning.factory("sleep 0", new ProcessOutputString())

    assert 0 == running.waitFor(2000)
  }

  @Test(expected = InterruptedException)
  void should_spawn_a_process_with_a_timeout_that_has_expired() {
    def running = ProcessRunning.factory("sleep 1", new ProcessOutputString())

    running.waitFor(10)
  }

  @Test
  void should_wait_for_a_process_that_has_not_finished_yet() {
    def running = ProcessRunning.factory("sleep 1", new ProcessOutputString())

    assert 0 == running.waitFor()
  }

  @Test
  void should_wait_for_a_process_which_is_already_complete() {
    def running = ProcessRunning.factory("sleep 0", new ProcessOutputString())
    running.waitFor()

    assert 0 == running.waitFor()
  }

  @Test
  void should_spawn_a_process_and_wait_forever_for_it_to_complete() {
    def running = ProcessRunning.factory("sleep 0", new ProcessOutputString())

    assert 0 == running.waitForever()
  }

  @Test
  void should_log_debug_output() {
    ProcessRunning.log.level= Log.LEVEL_DEBUG

    def text = Log.capture(ProcessRunning.log) {
      Process.start(["echo", "abc"])
    }

    assert text.contains("command=\"[echo, abc]\"; exitCode=\"0\"; output=\"true\"")
  }

  @Test
  void should_log_trace_output() {
    ProcessRunning.log.level= Log.LEVEL_TRACE

    def text = Log.capture(ProcessRunning.log) {
      Process.start(["echo", "abc"])
    }

    assert text.contains('output=\"abc')
  }
}


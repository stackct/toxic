package process

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

class ProcessOutputStringTest {
  @Test
  void should_write() {
    def output = new ProcessOutputString()
    output.builder = [redirectErrorStream: { mergeStreams -> }]
    output.process = [consumeProcessOutput: { stdout, stderr -> }]

    output.writeLine("hello world")

    assert output.output == "hello world"
  }
}

import groovy.json.JsonSlurper
import java.util.concurrent.CountDownLatch

def latch = new CountDownLatch(memory.parallel)
def outputs = Collections.synchronizedList([])

memory.parallel.times { index ->
  Thread.start {
    try {
      def cmd = []
      cmd << 'bash'
      cmd << '-c'
      cmd << "echo -n 'X'"

      def output = getOutputBuffer()

      assert 0 == execWithEnvNoLogging(cmd)

      if (output?.isEmpty()) {
        log.error("Failed to get output from stringbuffer ${index}")
        return
      }

      outputs.add(output)

    } finally {
      latch.countDown()
    }
  }
}

latch.await()

memory.outputCount = outputs.size()
memory.output = outputs.join(",")


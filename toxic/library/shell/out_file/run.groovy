import groovy.json.JsonSlurper
import java.util.concurrent.CountDownLatch

def latch = new CountDownLatch(memory.parallel)
def outputs = Collections.synchronizedList([])

memory.parallel.times { index ->
  Thread.start {
    def tempFile = null
    try {
      tempFile = File.createTempFile("cmd_output_${index}_", ".txt")

      def cmd = []
      cmd << 'bash'
      cmd << '-c'
      cmd << "echo -n 'X' >> ${tempFile.absolutePath}"

      assert 0 == execWithEnvNoLogging(cmd)

      if (tempFile.exists()) {
        def output = tempFile.text

        if (output?.isEmpty()) {
          log.error("Failed to get output from file ${index}")
          return
        }

        outputs.add(output)
      } else {
        log.error("Failed to create temp file for thread ${index}")
      }

    } finally {
      tempFile?.delete()
      latch.countDown()
    }
  }
}

latch.await()

memory.outputCount = outputs.size()
memory.output = outputs.join(",")

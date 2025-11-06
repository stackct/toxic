import groovy.json.JsonSlurper
import java.util.concurrent.CountDownLatch

def latch = new CountDownLatch(memory.parallel)
def bodies = Collections.synchronizedList([])
def statusCodes = Collections.synchronizedList([])

memory.parallel.times { index ->
  Thread.start {
    def tempFile = null
    try {
      tempFile = File.createTempFile("cmd_output_${index}_", ".txt")

      def cmd = []
      cmd << 'curl'
      cmd << '-isk'
      cmd << '-o'
      cmd << tempFile.absolutePath
      cmd << "https://example.com"

      assert 0 == execWithEnvNoLogging(cmd)

      if (tempFile.exists()) {
        def output = tempFile.text

        if (output?.isEmpty()) {
          log.error("Failed to get output from file ${index}")
          return
        }

        // Response is headers and body - split on first empty line
        def parts = output.split(/\r?\n\r?\n/, 2)
        def headers = parts[0]
       
        // Extract status code from first line (e.g., "HTTP/1.1 200 OK")
        def firstLine = headers.split(/\r?\n/)[0]
        def statusMatch = firstLine =~ /HTTP\/[\d.]+\s+(\d+)/
        if (statusMatch) {
          statusCodes.add(statusMatch[0][1])
        }

        if (parts.length > 1) {
          bodies.add(parts[1])
        }
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

memory.statusCodes = statusCodes.join(",")
memory.bodies = bodies.join("")
memory.count = bodies.size()


package toxic

import org.apache.log4j.*
import toxic.TaskResult
import toxic.TaskResultsFormatter
import org.apache.commons.io.*
import groovy.json.*
import java.util.zip.*

public class SerializedFormatter implements TaskResultsFormatter {
  private static Logger log = Logger.getLogger(this)

  def props

  public void init(def props) {
    this.props = props
  }
  
  public File getTargetFile(filename) {
    def file = new File(filename)
    if (file.isDirectory()) {
      throw new IllegalArgumentException("Invalid target file; serializedFile=${filename}")
    } else if (!file.exists()) {
      file.getParentFile()?.mkdirs()
    }
    return file
  }
  
  public File getResultsFile() {
    return getTargetFile(props.serializedFile)
  }

  public File getSummaryFile() {
    return getTargetFile(props.serializedSummaryFile)
  }

  public void write(results, file) {
    try {
      if (file.exists()) {
        file.delete()
      }
      
      if (!results) return

      def json = JsonOutput.toJson(results)
      log.info("Starting serialization and compression of results into JSON stream; file=${file.canonicalPath}; jsonSize=${json.size()}; resultCount=${results.size()}")
      def zip = new GZIPOutputStream(new FileOutputStream(file))
      zip.write(json.getBytes())
      zip.close()
      log.info("Completed serialization and compression of results into JSON stream; file=${file.canonicalPath}; compressedSize=${file.size()}")
    } catch (Exception e) {
      log.error("Failed to serialize results; file=${file}; reason=${e}", e)
    }
  }
  
  public void format(List<TaskResult> results) {
    write(results.collect { it.toSimple() }, resultsFile)
  }
  
  public void formatSummary(summary) {
    write(summary, summaryFile)
  }

  public def read(file) {
    def results
    if (file.isFile()) {
      try {
        def zip = new GZIPInputStream(new FileInputStream(file))
        log.debug("Decompressing JSON results into buffer; file=${file.canonicalPath}; compressedSize=${file.size()}")
        def bos = new ByteArrayOutputStream()
        int actualRead = 0
        def tmpSize = 1024
        def tmp = new byte[tmpSize]
        while (actualRead != -1) {
          actualRead = zip.read(tmp, 0, tmpSize)
          if (actualRead > 0) {
            bos.write(tmp, 0, actualRead)
          }
        }
        results = new JsonSlurper().parseText(bos.toString())
        log.info("Completed deserialization of JSON results into results list; file=${file.canonicalPath}; jsonSize=${bos.size()}; compressedSize=${file.size()}; resultCount=${results.size()}")
      } catch (Exception e) {
        log.error("Unable to parse historic results; file=${file.canonicalPath}; reason=${e}", e)
      }
    }
    return results
  }
  
  public List<TaskResult> parse() {
    def results = read(resultsFile)
    return results.collect { new TaskResult(it) }
  }
  
  public def parseSummary() {
    return read(summaryFile)
  }
}

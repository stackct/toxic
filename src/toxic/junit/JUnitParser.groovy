
package toxic.junit

import toxic.TaskResult
import toxic.TaskResultsFormatter
import org.apache.log4j.*

public class JUnitParser {
  private static Logger log = Logger.getLogger(JUnitParser.class)

  def parseDir(File dir, List taskResults) {
    def stats = [:]
    stats.suites = 0
    stats.failures = 0

    def isDir = dir.exists() && dir.isDirectory()
    log.info("Parsing directory for XML results; path=${dir}; isDirectory=${isDir}")
    if (isDir) {
      dir.eachFile {
        log.info("Found file; file=${it}; isDirectory=${it.isDirectory()}")
        if (!it.isDirectory() && it.name.endsWith(".xml")) {
          log.info("Parsing file; file=${it}; isDirectory=${it.isDirectory()}")
          def fileStats = parseFile(it, taskResults)
          stats.suites += fileStats.suites
          stats.failures += fileStats.failures
        }
      }
    }
    return stats
  }
  
  def parseFile(File xmlFile, List taskResults) {
    def stats = [:]
    stats.suites = 0
    stats.failures = 0

    long startTime = 0 // Default to beginning of time if tests do not include start 
    if (!xmlFile?.isFile()) {
      log.warn("jUnit XML file not found; file=${xmlFile}")
      return stats
    }
    
    try {
      def xml = new XmlSlurper().parse(xmlFile)
      def fileTimestamp
      if (xml.name() == "testsuite") {
        // This is an single test suite results file
        xml = [testsuite:xml]
      } else {
        fileTimestamp = xml.@timestamp?.toString()
      }
      log.info("JUnit xml file summary; suites=${xml?.testsuite?.size()}")
      xml?.testsuite?.each { suite ->
        def suiteFailed = false
        int discoveredTests = 0
        def timestamp = suite.@timestamp?.isEmpty() ? fileTimestamp : suite.@timestamp?.toString()
        suite.testcase?.each { testcase ->
          def family = testcase.@classname?.toString() ?: suite.@name?.toString()
          def id = family + "." + testcase.@name.toString()
          def alreadyExists = taskResults.find { it.id == id }
          if (alreadyExists) return
          
          discoveredTests++
          def result = new TaskResult(id, family, testcase.@name.toString(), null)
          def failure = testcase.failure
          failure = failure?.isEmpty() ? testcase.error : failure
          failure = failure?.isEmpty() ? null : failure
          if (failure) {
            result.success = false
            result.type = failure.@type.toString()
            if (result.type) {
              result.error = failure.@message.toString()
            }
            suiteFailed = true
          } else {
            result.success = true
          }
          if (timestamp) {
            result.startTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss", timestamp).time
            result.stopTime = result.startTime + (long)Math.round(testcase.@time.toString().toDouble() * 1000.0)
          } else {
            result.startTime = startTime
            result.stopTime = result.startTime + (long)Math.round(testcase.@time.toString().toDouble() * 1000.0)
            startTime = result.stopTime
          }
          taskResults << result
        }      
        if (discoveredTests > 0) {
          stats.suites++
          if (suiteFailed) stats.failures++
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse jUnit XML file; file=${xmlFile}; reason=${e}", e)
    }
    return stats
  }
}

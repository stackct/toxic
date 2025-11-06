
package toxic.junit

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import toxic.TaskResult
import toxic.TaskResultsFormatter

public class JUnitFormatter implements TaskResultsFormatter {
  def props
  def outFile

  public void init(def props) {
    this.props = props
    outFile = new File(props.junitFile)
    if (outFile.isDirectory()) {
      throw new IllegalArgumentException("Invalid output file; junitFile=" + props.junitFile)
    } else if (!outFile.exists()) {
      outFile.mkdirs()
    }
  }

  protected String xmlEscape(def s) {
    def result = s
    if (s) {
      result = result.replaceAll("&","&amp;")
      result = result.replaceAll("<","&lt;")
      result = result.replaceAll(">","&gt;")
      result = result.replaceAll("\"","'")
      result = result.replaceAll("'","&apos;")
    }
    return result
  }

  public String toXml(List<TaskResult> results) {
    def total = results?.size()
    def fail = 0
    def errors = 0
    def totalElapsedTimeMs = 0

    def testCasesXml = ""
    results?.each {
      def id = xmlEscape(it.id)
      def name = xmlEscape(it.name)
      def family = xmlEscape(it.family)
      def elapsedTimeSecs = it.elapsedTimeMillis / 1000
      def messages = it.messages?.join('\n')
      totalElapsedTimeMs += it.elapsedTimeMillis

      if(!it.family.endsWith('.test')){
        // discard functions and test file entries for junit report
        total--
      } else if (it.success) {
        testCasesXml += "  <testcase name='$name ($id)' time='$elapsedTimeSecs' classname='$family'/>\n"
      } else {
        def element = "failure"
        def type = ""
        def details = ""
        def stack = ""
        if (it.error != null) {
          type = xmlEscape(it.errorType)
          details = xmlEscape(it.error)
          fail++
        }
        
        //Remove any Ansi color codes for the file output
        String messagesData = Pattern.compile("\u001b\\[\\d+(;\\d+)*m").matcher(messages).replaceAll("");
    
        testCasesXml += "  <testcase name='$name ($id)' time='$elapsedTimeSecs' classname='$family'>\n"
        testCasesXml += "    <$element type='$type' message='$details'>\n"
        testCasesXml += "<![CDATA[$messagesData]]>\n"
        testCasesXml += "    </$element>\n"
        testCasesXml += "  </testcase>\n"
      }
    }

    def xml = "<?xml version='1.0' encoding='UTF-8'?>\n"
    xml += "<testsuite name='Toxic' failures='$fail' tests='$total' time='${totalElapsedTimeMs / 1000}' errors='$errors'>\n"
    xml += testCasesXml
    xml += "</testsuite>"

    return xml
  }

  public void format(List<TaskResult> results) {
    if (outFile.exists()) {
      outFile.delete()
    }

    outFile.text = toXml(results)
  }
}

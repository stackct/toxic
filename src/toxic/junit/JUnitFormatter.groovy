
package toxic.junit

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
      totalElapsedTimeMs += it.elapsedTimeMillis

      if (it.success) {
        testCasesXml += "  <testcase name='$name ($id)' time='$elapsedTimeSecs' classname='$family'/>\n"
      } else {
        def element = "failure"
        def type = ""
        def details = ""
        def stack = ""
        if (it.error != null) {
          type = xmlEscape(it.error.class.name)
          details = xmlEscape(it.error)
          fail++
        }

        testCasesXml += "  <testcase name='$name ($id)' time='$elapsedTimeSecs' classname='$family'>\n"
        testCasesXml += "    <$element type='$type' message='$details'>\n"
        testCasesXml += "      <![CDATA[$stack]]>\n"
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

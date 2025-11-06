
package toxic.junit

import toxic.TaskResult
import org.junit.*

public class JUnitFormatterTest {
  @Test
  public void testFormat() {
    TaskResult tr = new TaskResult("1", "afile.test", "test_req.xml", TaskResult.class.name)
    tr.success=true
    TaskResult tr2 = new TaskResult("2", "testdir", "testdir", TaskResult.class.name)
    tr2.success=true
    def results = [tr,tr2]
    def actual = new JUnitFormatter().toXml(results)
    def expected = """<?xml version='1.0' encoding='UTF-8'?>
<testsuite name='Toxic' failures='0' tests='1' time='0.000' errors='0'>
  <testcase name='test_req.xml (1)' time='0.000' classname='afile.test'/>
</testsuite>"""
    actual = actual.replaceAll(/time='.*?'/, "time='0.000'")
    assert actual.trim() == expected.trim()
  }

  @Test
  public void testFormatFailureWithStack() {
    TaskResult tr = new TaskResult("1", "afile.test", "test_req.xml", TaskResult.class.name)
    tr.success=false
    tr.errorType = "errorType"
    tr.error = "error"
    tr.messages = ["message1","\tmessage2"]
    def results = [tr]
    def actual = new JUnitFormatter().toXml(results)
    def expected = """<?xml version='1.0' encoding='UTF-8'?>
<testsuite name='Toxic' failures='1' tests='1' time='0.000' errors='0'>
  <testcase name='test_req.xml (1)' time='0.000' classname='afile.test'>
    <failure type='errorType' message='error'>
<![CDATA[message1
\tmessage2]]>
    </failure>
  </testcase>
</testsuite>"""
    actual = actual.replaceAll(/time='.*?'/, "time='0.000'")
    assert actual.trim() == expected.trim()
  }

  @Test
  public void testFormatFailureWithoutStack() {
    TaskResult tr = new TaskResult("1", "afile.test", "test_req.xml", TaskResult.class.name)
    tr.success=false
    tr.errorType = "errorType"
    tr.error = "error"
    tr.messages = ["message1","\tmessage2"]
    def results = [tr]
    def actual = new JUnitFormatter().toXml(results)
    def expected = """<?xml version='1.0' encoding='UTF-8'?>
<testsuite name='Toxic' failures='1' tests='1' time='0.000' errors='0'>
  <testcase name='test_req.xml (1)' time='0.000' classname='afile.test'>
    <failure type='errorType' message='error'>
<![CDATA[message1
\tmessage2]]>
    </failure>
  </testcase>
</testsuite>"""
    actual = actual.replaceAll(/time='.*?'/, "time='0.000'")
    assert actual.trim() == expected.trim()
  }
  @Test
  public void testXmlEscape() {
    def actual = new JUnitFormatter().xmlEscape("testdirname<@*&\"\'")
    def expected = "testdirname&lt;@*&amp;&apos;&apos;"
    assert actual == expected
  }
}
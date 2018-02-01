
package toxic.junit

import toxic.TaskResult
import org.junit.*

public class JUnitFormatterTest {
  @Test
  public void testFormat() {
    TaskResult tr = new TaskResult("1", "testdirname", "test_req.xml", TaskResult.class.name)
    tr.success=true
    def results = [tr]
    def actual = new JUnitFormatter().toXml(results)
    def expected = """<?xml version='1.0' encoding='UTF-8'?>
<testsuite name='Toxic' failures='0' tests='1' time='0.000' errors='0'>
  <testcase name='test_req.xml (1)' time='0.000' classname='testdirname'/>
</testsuite>"""
    actual = actual.replaceAll(/time='.*?'/, "time='0.000'")
    assert actual.trim() == expected.trim()
  }

  public void testEscapeXml() {
    def actual = new JUnitFormatter().escapeXml("testdirname<@*&\"\'")
    def expected = "testdirname&lt;@*&amp;&apos;&apos;"
    assert actual == expected
  }
}
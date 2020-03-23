package toxic.junit

import groovy.mock.interceptor.*

import java.nio.file.*

import org.apache.commons.io.*
import org.junit.*

import toxic.*
import toxic.groovy.*

public class XUnitJUnitParserTest {
  def tmpFile

  @Before
  void before() {
    tmpFile = File.createTempFile("tmp.throwaway", this.class.name)
  }

  @After
  void after() {
    FileUtils.metaClass = null
    File.metaClass = null
    tmpFile?.delete()
  }

  @Test
  void should_parse_junit_xmls_in_dir() {
    def exists
    def parsed = []
    def f1 = new File("test1.xml")
    def f2 = new File("test2.txt")
    def f3 = new File("test3.xml")
    def files = [f1,f2,f3]
    File.metaClass.eachFile = { Closure c -> files.each { c(it)} }
    File.metaClass.exists = { -> exists = true; name.contains("somedir") }
    File.metaClass.isDirectory = { -> name.contains("somedir") }
    def msgs = []
    def parser = new XUnitJUnitParser() {
      def log = new Object() { def info(msg) { msgs << msg }}
      def parseFile(File xmlFile, List results) { parsed << xmlFile; return [suites: 1,failures:2] }
    }
    def actualResults = []
    def stats = parser.parseDir(new File("somedir"), actualResults)
    assert stats.suites == 2
    assert stats.failures == 4
    assert parsed == [f1,f3]
    assert exists
  }

  @Test
  void should_not_parse_junit_xmls_in_dir_if_dir_missing() {
    def parsed = []
    File.metaClass.eachFile = { Closure c -> throw new Exception("should not get this far") }
    File.metaClass.exists = { -> false }
    File.metaClass.isDirectory = { -> false }
    def msgs = []
    def parser = new XUnitJUnitParser() {
      def log = new Object() { def info(msg) { msgs << msg }}
      def parseFile(File xmlFile, List results) { parsed << xmlFile; return [suites:0,failures:0] }
    }
    def actualResults = []
    def stats = parser.parseDir(new File("nothing"), actualResults)
    assert !stats.suites
    assert !stats.failures
    assert !parsed
  }

  @Test
  void should_parse_xunit_xml() {
    tmpFile.text = '''<testsuite name="Cafecito Tests" tests="4" failures="0" errors="1" skipped="0" timestamp="Wed, 19 Feb 2020 15:53:29 GMT" time="23.937">
<testcase classname="/app/#/Takeoff/: takeoff templates created" name="by copying search template name to create takeoff panel" time="0.172"/>
<testcase classname="/app/#/Takeoff/: takeoff templates created when start measuring" name="will error if no measurement type selected" time="6.069"/>
<testcase classname="/app/#/Takeoff/: takeoff templates created when start measuring" name="will error if no page is opened" time="6.036"/>
<testcase classname="/app/#/Takeoff/: takeoff templates created when start measuring" name="is completed" time="10.009"><failure>Timeout of 10000ms exceeded. For async tests and hooks, ensure "done()" is called; if returning a Promise, ensure it resolves.
Error: Timeout of 10000ms exceeded. For async tests and hooks, ensure "done()" is called; if returning a Promise, ensure it resolves.
    at Test.Runnable._timeoutError (http://localhost:5000/vwa/1.0.0/cafecito-tests.js:5441:10)
    at http://localhost:5000/vwa/1.0.0/cafecito-tests.js:5252:24</failure></testcase>
</testsuite>'''

    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 4

    def firstResult = actualResults.first()
    assert firstResult.id         == "/app/#/Takeoff/: takeoff templates created.by copying search template name to create takeoff panel"
    assert firstResult.family     == "/app/#/Takeoff/: takeoff templates created"
    assert firstResult.name       == "by copying search template name to create takeoff panel"
    assert firstResult.type       == null
    assert firstResult.success    == true
    assert firstResult.startTime  == 1582127609000
    assert firstResult.stopTime > firstResult.startTime
    assert firstResult.error      == null

    def errorResult = actualResults[3];
    assert errorResult.type       == ""
    assert errorResult.success    == false
    assert errorResult.error.toString() == '''Timeout of 10000ms exceeded. For async tests and hooks, ensure "done()" is called; if returning a Promise, ensure it resolves.
    at Test.Runnable._timeoutError (http://localhost:5000/vwa/1.0.0/cafecito-tests.js:5441:10)
    at http://localhost:5000/vwa/1.0.0/cafecito-tests.js:5252:24'''
  }

  @Test
  void should_parse_junit_xml() {
    tmpFile.text = '''<testsuites>
                      <testsuite errors="0" failures="1" hostname="local" id="0" name="ToxicSlackHandlerTest" package="toxic.slack" skipped="0" tests="2" time="0.516" timestamp="2015-06-20T05:36:20">
                      <properties>...</properties>
                      <testcase classname="toxic.slack.ToxicSlackHandlerTest" name="should_fetch_external_url" time="0.288"/>
                      <testcase classname="toxic.slack.ToxicSlackHandlerTest" name="should_handle_incomplete_commands" time="0.05">
                      <failure message="assert sh.handleCommand(bot, [text:&quot;run x&lt;@bot1&gt;&quot;]) == &quot;invalid job id: xd&quot; | | | | | | | false | | toxic.slack.SlackBot@e01b3fe | invalid job id: x toxic.slack.ToxicSlackHandler@11cfefe1" type="junit.framework.AssertionFailedError">
                      junit.framework.AssertionFailedError: assert sh.handleCommand(bot, [text:&quot;run x&lt;@bot1&gt;&quot;]) == &quot;invalid job id: xd&quot; | | | | | | | false | | toxic.slack.SlackBot@e01b3fe | invalid job id: x toxic.slack.ToxicSlackHandler@11cfefe1 at org.codehaus.groovy.runtime.InvokerHelper.assertFailed(InvokerHelper.java:399) at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.assertFailed(ScriptBytecodeAdapter.java:655) at toxic.slack.ToxicSlackHandlerTest.should_handle_incomplete_commands(ToxicSlackHandlerTest.groovy:51)
                      </failure>
                      </testcase>
                      <system-out>
                      <![CDATA[ ]]>
                      </system-out>
                      <system-err>
                      <![CDATA[ ]]>
                      </system-err>
                      </testsuite>
                      </testsuites>'''

    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 2
    assert actualResults[0].id == "toxic.slack.ToxicSlackHandlerTest.should_fetch_external_url"
    assert actualResults[0].family == "toxic.slack.ToxicSlackHandlerTest"
    assert actualResults[0].name == "should_fetch_external_url"
    assert actualResults[0].type == null
    assert actualResults[0].success == true
    assert actualResults[0].startTime > 0
    assert actualResults[0].stopTime > actualResults[0].startTime
    assert actualResults[0].error == null

    assert actualResults[1].id == "toxic.slack.ToxicSlackHandlerTest.should_handle_incomplete_commands"
    assert actualResults[1].family == "toxic.slack.ToxicSlackHandlerTest"
    assert actualResults[1].name == "should_handle_incomplete_commands"
    assert actualResults[1].type == "junit.framework.AssertionFailedError"
    assert actualResults[1].success == false
    assert actualResults[1].startTime > 0
    assert actualResults[1].stopTime > actualResults[1].startTime
    assert actualResults[1].error?.toString()?.contains("handleCommand")

    assert stats.suites == 1
    assert stats.failures == 1

    // Reparse should not readd the same test results
    stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 2
    assert stats.suites == 0
    assert stats.failures == 0
  }

  @Test
  void should_parse_junit_spec_format_xml() {
    tmpFile.text = '''<?xml version="1.0" encoding="UTF-8"?>
<testsuites errors="0" failures="0" skipped="0" tests="850" time="69.078" timestamp="2016-10-13T13:52:06Z">
  <testsuite name="MessageParser" tests="21" errors="0" failures="0" skipped="0">
    <properties/>
    <testcase name="MessageParser load translation yml file the file exists" time="0.021">
    </testcase>
    <testcase name="MessageParser load translation yml file but there is no language passed no lang is provided" time="0.017">
    </testcase>
  </testsuite>
  <testsuite name="Hash" tests="9" errors="0" failures="0" skipped="0">
    <properties/>
    <testcase name="Hash should extract a label from data" time="0.001">
    </testcase>
  </testsuite>
</testsuites>'''

    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 3
    assert actualResults[0].id == "MessageParser.MessageParser load translation yml file the file exists"
    assert actualResults[0].family == "MessageParser"
    assert actualResults[0].name == "MessageParser load translation yml file the file exists"
    assert actualResults[0].type == null
    assert actualResults[0].success == true
    assert actualResults[0].startTime > 0
    assert actualResults[0].stopTime > actualResults[0].startTime
    assert actualResults[0].error == null

    assert actualResults[1].id == "MessageParser.MessageParser load translation yml file but there is no language passed no lang is provided"
    assert actualResults[1].family == "MessageParser"
    assert actualResults[1].name == "MessageParser load translation yml file but there is no language passed no lang is provided"
    assert actualResults[1].type == null
    assert actualResults[1].success == true
    assert actualResults[1].startTime > 0
    assert actualResults[1].stopTime > actualResults[1].startTime
    assert actualResults[1].error == null

    assert stats.suites == 2
    assert stats.failures == 0

    // Reparse should not readd the same test results
    stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 3
    assert stats.suites == 0
    assert stats.failures == 0
  }

  @Test
  void should_parse_junit_xml_alternate_format() {
    tmpFile.text = '''<testsuite errors="0" failures="1" hostname="local" id="0" name="ToxicSlackHandlerTest" package="toxic.slack" skipped="0" tests="2" time="0.516">
                      <properties>...</properties>
                      <testcase classname="toxic.slack.ToxicSlackHandlerTest" name="should_fetch_external_url" time="0.288"/>
                      <system-out>
                      <![CDATA[ ]]>
                      </system-out>
                      <system-err>
                      <![CDATA[ ]]>
                      </system-err>
                      </testsuite>'''
    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 1
    assert actualResults[0].id == "toxic.slack.ToxicSlackHandlerTest.should_fetch_external_url"
    assert actualResults[0].family == "toxic.slack.ToxicSlackHandlerTest"
    assert actualResults[0].name == "should_fetch_external_url"
    assert actualResults[0].type == null
    assert actualResults[0].success == true
    assert actualResults[0].startTime == 0
    assert actualResults[0].stopTime == 288
    assert actualResults[0].error == null
  }

  @Test
  void should_parse_junit_nightwatch_format() {
    tmpFile.text = '''
<testsuites errors="0"
            failures="1"
            tests="1">
  <testsuite name="downloadSupportDocs"
    errors="0" failures="1" hostname="" id="" package="downloadSupportDocs" skipped="0"
    tests="1" time="127.4" timestamp="">
    <testcase name="Should successfully download support documents" classname="downloadSupportDocs" time="127.4" assertions="13">
      <failure message="Timed out while waiting for element &lt;i[data-id=&#34;modifyFolderByName-int-ksip8z0lbo&#34;]:not([class*=&#34;disable&#34;])&gt; to be visible for 120000 milliseconds. - expected &#34;visible&#34; but got: &#34;not visible&#34;">    at CommandInstance.ClickButton.command (/opt/node/nightwatch/dist/common/commands/clickButton.js:26:14)
    at &lt;anonymous&gt;
    at process._tickCallback (internal/process/next_tick.js:188:7)</failure>
    <failure message="    at CommandInstance.ClickButton.command (/opt/node/nightwatch/dist/common/commands/clickButton.js:26:14)">    at &lt;anonymous&gt;
    at process._tickCallback (internal/process/next_tick.js:188:7)</failure>
    </testcase>
  </testsuite>
</testsuites>'''

    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 1
    assert actualResults[0].id == "downloadSupportDocs.Should successfully download support documents"
    assert actualResults[0].family == "downloadSupportDocs"
    assert actualResults[0].name == "Should successfully download support documents"
    assert actualResults[0].type == ""
    assert actualResults[0].success == false
    assert actualResults[0].startTime == 0
    assert actualResults[0].stopTime > actualResults[0].startTime
    assert actualResults[0].error.toString().contains("modifyFolderByName")

    assert stats.suites == 1
    assert stats.failures == 1
  }

  @Test
  void should_parse_junit_cs_format() {
    tmpFile.text = '''
<testsuites xmlns:a="http://microsoft.com/schemas/VisualStudio/TeamTest/2006" xmlns:b="http://microsoft.com/schemas/VisualStudio/TeamTest/2010">
  <testsuite name="MSBuildTest" tests="462" time="0" timestamp="2019-07-14T02:31:08.4439510+00:00" failures="5" errors="0" skipped="0">
    <testcase classname="CTKO.CombinedServer.Tests" name="CTKO.Api.Controllers.v2.PagesControllerTest.ShouldLogMessageIfAccessViolationOccurs" time="0.171">
      <failure>
                    MESSAGE:
                    Assert.Contains() Failure
Not found: userId
In value:  KeyCollection&lt;String, Object&gt; ["exceptionMessage", "baseExceptionMessage"]
                    +++++++++++++++++++
                    STACK TRACE:
                       at CTKO.LibTest.Log.LogTracker.AssertIsLogged(LogEventInfo logEvent, Object props, String nestedMessage) in /src/lib/src/CTKO.LibTest/Log/LogTracker.cs:line 274
   at CTKO.LibTest.Log.LogTracker.AssertIsLogged(LogLevel level, String message, Object props, String nestedMessage) in /src/lib/src/CTKO.LibTest/Log/LogTracker.cs:line 246
   at CTKO.LibTest.Log.LogTracker.AssertIsInfo(String message, Object props, String nestedMessage) in /src/lib/src/CTKO.LibTest/Log/LogTracker.cs:line 232
   at CTKO.Api.Controllers.v2.PagesControllerTest.ShouldLogMessageIfAccessViolationOccurs() in /src/app/tests/CTKO.CombinedServer.Tests/Controllers/Api/v2/PagesControllerTest.cs:line 184</failure>
    </testcase>
  </testsuite>
</testsuites>'''

    def actualResults = []
    def stats = new XUnitJUnitParser().parseFile(tmpFile, actualResults)
    assert actualResults.size() == 1
    assert actualResults[0].id == "CTKO.CombinedServer.Tests.CTKO.Api.Controllers.v2.PagesControllerTest.ShouldLogMessageIfAccessViolationOccurs"
    assert actualResults[0].family == "CTKO.CombinedServer.Tests"
    assert actualResults[0].name == "CTKO.Api.Controllers.v2.PagesControllerTest.ShouldLogMessageIfAccessViolationOccurs"
    assert actualResults[0].type == ""
    assert actualResults[0].success == false
    assert actualResults[0].startTime > 0
    assert actualResults[0].stopTime > actualResults[0].startTime
    assert actualResults[0].error.toString().contains("baseExceptionMessage")

    assert stats.suites == 1
    assert stats.failures == 1
  }

}


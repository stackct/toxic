package toxic.splunk

import org.junit.*
import toxic.ToxicProperties

public class SplunkTaskTest {
  @Test(expected=IllegalArgumentException)
  public void should_fail_init() {
    def test = new SplunkTask()
    test.init("test", new Properties())
  }

  @Test
  public void should_init() {
    def test = new SplunkTask() {
      protected String replace(def str) { return "something" }
    }
    def file = File.createTempFile(this.class.name + "should_init", "_req.splunk")
    file.text = """
splunk_hostname=%var%
arg1=foo
arg2=b&r
"""
    def props = new Properties()
    try {
      test.init(file, props)
    } finally {
      file.delete()
    }
    assert props.splunk_hostname == "something"
    assert test.reqContent in ["arg1=foo&arg2=b%26r", "arg2=b%26r&arg1=foo"]
  }
  
  @Test
  public void should_init_with_data() {
    def test = new SplunkTask() {
      protected String replace(def str) { return "something" }
    }
    def file = File.createTempFile(this.class.name + "should_init", "_req.splunk")
    file.text = """
splunk_hostname=%var%
data=hello there & good bye
arg2=b&r
"""
    def props = new Properties()
    try {
      test.init(file, props)
    } finally {
      file.delete()
    }
    assert props.splunk_hostname == "something"
    assert test.reqContent == "hello there & good bye"
  }

  @Test
  void should_dotask() {
    def transmitted
    def test = new SplunkTask() {
      protected String transmit(request, expectedResponse, memory) { transmitted=true}
    }
    def file = File.createTempFile(this.class.name + "should_init", "_req.splunk")
    file.text = """
splunk_hostname=%var%
data=hello there & good bye
arg2=b&r
"""
    try {
      test.init(file, [:])
    } finally {
      file.delete()
    }
    test.reqContent = ""
    test.props = new Properties()
    test.props.xmlHost = "bar"
    def props = new ToxicProperties()
    props.xmlHost = "foo"
    test.doTask(props)
    assert props.xmlHost == "foo"
    assert transmitted
  }

  @Test
  void should_add_headers() {
    def test = new SplunkTask()
    test.props = new Properties()
    test.props["xml.header.foo"] = "bar"
    test.props["splunk.header.s1"] = "22"
    def actual = test.headers()
    assert actual.contains("foo: bar")
    assert actual.contains("s1: 22")
  }

  def sidResp = """HTTP/1.1 201 Created
Date: Tue, 13 Oct 2015 22:11:15 GMT
Expires: Thu, 26 Oct 1978 00:00:00 GMT
Cache-Control: no-store, no-cache, must-revalidate, max-age=0
Content-Type: text/xml; charset=UTF-8
X-Content-Type-Options: nosniff
Content-Length: 121
Location: /services/search/jobs/admin__admin__search__Talkers_at_1444774275_7
Vary: Authorization
Connection: Close
X-Frame-Options: SAMEORIGIN
Server: Splunkd
\r\n\r\n
<?xml version="1.0" encoding="UTF-8"?>
<response>
<sid>admin__admin__search__Talkers_at_1444774275_7</sid>
</response>
  """
  
  @Test
  void should_parse_sid() {
    def test = new SplunkTask()
    assert test.parseSid(sidResp) == "admin__admin__search__Talkers_at_1444774275_7"
  }

  def statusResp = """HTTP/1.1 200 OK
Date: Tue, 13 Oct 2015 22:11:15 GMT
Expires: Thu, 26 Oct 1978 00:00:00 GMT
Cache-Control: no-store, no-cache, must-revalidate, max-age=0
Content-Type: text/xml; charset=UTF-8
X-Content-Type-Options: nosniff
Content-Length: 14649
Vary: Authorization
Connection: Close
X-Frame-Options: SAMEORIGIN
Server: Splunkd
\r\n\r\n
<?xml version="1.0" encoding="UTF-8"?>
<!--This is to override browser formatting; see server.conf[httpServer] to disable. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .-->
<?xml-stylesheet type="text/xml" href="/static/atom.xsl"?>
<entry xmlns="http://www.w3.org/2005/Atom" xmlns:s="http://dev.splunk.com/ns/rest" xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
<title>search source=slack | stats count by channelName</title>
<id>http://splunk.mycompany.invalid/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7</id>
<updated>2015-10-13T18:11:15.837-04:00</updated>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7" rel="alternate"/>
<published>2015-10-13T18:11:15.000-04:00</published>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/search.log" rel="search.log"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/events" rel="events"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/results" rel="results"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/results_preview" rel="results_preview"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/timeline" rel="timeline"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/summary" rel="summary"/>
<link href="/services/search/jobs/admin__admin__search__Talkers_at_1444774275_7/control" rel="control"/>
<author>
  <name>admin</name>
</author>
<content type="text/xml">
  <s:dict>
    <s:key name="canSummarize">1</s:key>
    <s:key name="cursorTime">1969-12-31T19:00:00.000-05:00</s:key>
    <s:key name="defaultSaveTTL">604800</s:key>
    <s:key name="defaultTTL">600</s:key>
    <s:key name="delegate">admin</s:key>
    <s:key name="diskUsage">69632</s:key>
    <s:key name="dispatchState">DONE</s:key>
    <s:key name="doneProgress">1.00000</s:key>
    <s:key name="dropCount">0</s:key>
    <s:key name="earliestTime">2010-03-31T00:00:00.000-04:00</s:key>
    <s:key name="eventAvailableCount">0</s:key>
    <s:key name="eventCount">11131</s:key>
    <s:key name="eventFieldCount">0</s:key>
    <s:key name="eventIsStreaming">1</s:key>
    <s:key name="eventIsTruncated">1</s:key>
    <s:key name="eventSearch">search source=slack </s:key>
    <s:key name="eventSorting">none</s:key>
    <s:key name="isBatchModeSearch">1</s:key>
    <s:key name="isDone">1</s:key>
    <s:key name="isFailed">0</s:key>
    <s:key name="isFinalized">0</s:key>
    <s:key name="isGoodSummarizationCandidate">1</s:key>
    <s:key name="isPaused">0</s:key>
    <s:key name="isPreviewEnabled">0</s:key>
    <s:key name="isRealTimeSearch">0</s:key>
    <s:key name="isRemoteTimeline">0</s:key>
    <s:key name="isSaved">0</s:key>
    <s:key name="isSavedSearch">1</s:key>
    <s:key name="isTimeCursored">1</s:key>
    <s:key name="isZombie">0</s:key>
    <s:key name="keywords">source::slack</s:key>
    <s:key name="label">Talkers</s:key>
    <s:key name="normalizedSearch">litsearch source=slack | addinfo type=count label=prereport_events | fields keepcolorder=t "channelName" "prestats_reserved_*" "psrsvd_*" | prestats count by channelName</s:key>
    <s:key name="numPreviews">0</s:key>
    <s:key name="pid">22425</s:key>
    <s:key name="priority">5</s:key>
    <s:key name="reduceSearch">sistats count by channelName</s:key>
    <s:key name="remoteSearch">litsearch source=slack | addinfo  type=count label=prereport_events | fields  keepcolorder=t "channelName" "prestats_reserved_*" "psrsvd_*" | prestats  count by channelName</s:key>
    <s:key name="reportSearch">stats  count by channelName</s:key>
    <s:key name="resultCount">13</s:key>
    <s:key name="resultIsStreaming">0</s:key>
    <s:key name="resultPreviewCount">13</s:key>
    <s:key name="runDuration">0.395000</s:key>
    <s:key name="scanCount">11131</s:key>
    <s:key name="searchCanBeEventType">0</s:key>
    <s:key name="sid">admin__admin__search__Talkers_at_1444774275_7</s:key>
    <s:key name="statusBuckets">0</s:key>
    <s:key name="ttl">600</s:key>
    <s:key name="performance">
      <s:dict>
        <s:key name="command.addinfo">
          <s:dict>
            <s:key name="duration_secs">0.011000</s:key>
            <s:key name="invocations">11</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.fields">
          <s:dict>
            <s:key name="duration_secs">0.005000</s:key>
            <s:key name="invocations">11</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.prestats">
          <s:dict>
            <s:key name="duration_secs">0.037000</s:key>
            <s:key name="invocations">11</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">15</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search">
          <s:dict>
            <s:key name="duration_secs">0.181000</s:key>
            <s:key name="invocations">11</s:key>
            <s:key name="input_count">0</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.calcfields">
          <s:dict>
            <s:key name="duration_secs">0.006000</s:key>
            <s:key name="invocations">10</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.fieldalias">
          <s:dict>
            <s:key name="duration_secs">0.010000</s:key>
            <s:key name="invocations">10</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.filter">
          <s:dict>
            <s:key name="duration_secs">0.016000</s:key>
            <s:key name="invocations">10</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.index.usec_1_8">
          <s:dict>
            <s:key name="invocations">187</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.index.usec_8_64">
          <s:dict>
            <s:key name="invocations">2</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.kv">
          <s:dict>
            <s:key name="duration_secs">0.055000</s:key>
            <s:key name="invocations">10</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.lookups">
          <s:dict>
            <s:key name="duration_secs">0.007000</s:key>
            <s:key name="invocations">10</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.rawdata">
          <s:dict>
            <s:key name="duration_secs">0.094000</s:key>
            <s:key name="invocations">10</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.summary">
          <s:dict>
            <s:key name="duration_secs">0.002000</s:key>
            <s:key name="invocations">11</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.tags">
          <s:dict>
            <s:key name="duration_secs">0.007000</s:key>
            <s:key name="invocations">10</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.search.typer">
          <s:dict>
            <s:key name="duration_secs">0.006000</s:key>
            <s:key name="invocations">10</s:key>
            <s:key name="input_count">11131</s:key>
            <s:key name="output_count">11131</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.stats.execute_input">
          <s:dict>
            <s:key name="duration_secs">0.012000</s:key>
            <s:key name="invocations">12</s:key>
          </s:dict>
        </s:key>
        <s:key name="command.stats.execute_output">
          <s:dict>
            <s:key name="duration_secs">0.001000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.check_disk_usage">
          <s:dict>
            <s:key name="duration_secs">0.001000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.createdSearchResultInfrastructure">
          <s:dict>
            <s:key name="duration_secs">0.039000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.evaluate">
          <s:dict>
            <s:key name="duration_secs">0.053000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.evaluate.search">
          <s:dict>
            <s:key name="duration_secs">0.053000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.evaluate.stats">
          <s:dict>
            <s:key name="duration_secs">0.001000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.fetch">
          <s:dict>
            <s:key name="duration_secs">0.233000</s:key>
            <s:key name="invocations">12</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.localSearch">
          <s:dict>
            <s:key name="duration_secs">0.203000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.stream.local">
          <s:dict>
            <s:key name="duration_secs">0.208000</s:key>
            <s:key name="invocations">11</s:key>
          </s:dict>
        </s:key>
        <s:key name="dispatch.writeStatus">
          <s:dict>
            <s:key name="duration_secs">0.018000</s:key>
            <s:key name="invocations">5</s:key>
          </s:dict>
        </s:key>
        <s:key name="startup.configuration">
          <s:dict>
            <s:key name="duration_secs">0.015000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
        <s:key name="startup.handoff">
          <s:dict>
            <s:key name="duration_secs">0.039000</s:key>
            <s:key name="invocations">1</s:key>
          </s:dict>
        </s:key>
      </s:dict>
    </s:key>
    <s:key name="fieldMetadataStatic">
      <s:dict>
        <s:key name="channelName">
          <s:dict>
            <s:key name="type">unknown</s:key>
            <s:key name="groupby_rank">0</s:key>
          </s:dict>
        </s:key>
      </s:dict>
    </s:key>
    <s:key name="fieldMetadataResults">
      <s:dict>
        <s:key name="channelName">
          <s:dict>
            <s:key name="type">unknown</s:key>
            <s:key name="groupby_rank">0</s:key>
          </s:dict>
        </s:key>
      </s:dict>
    </s:key>
    <s:key name="messages">
      <s:dict/>
    </s:key>
    <s:key name="request">
      <s:dict>
        <s:key name="auto_cancel">0</s:key>
        <s:key name="auto_pause">0</s:key>
        <s:key name="buckets">0</s:key>
        <s:key name="earliest_time">0</s:key>
        <s:key name="index_earliest"></s:key>
        <s:key name="index_latest"></s:key>
        <s:key name="indexedRealtime"></s:key>
        <s:key name="latest_time"></s:key>
        <s:key name="lookups">1</s:key>
        <s:key name="max_count">500000</s:key>
        <s:key name="max_time">0</s:key>
        <s:key name="reduce_freq">10</s:key>
        <s:key name="rt_backfill">0</s:key>
        <s:key name="spawn_process">1</s:key>
        <s:key name="time_format">%FT%T.%Q%:z</s:key>
        <s:key name="ui_dispatch_app">search</s:key>
        <s:key name="ui_dispatch_view">search</s:key>
      </s:dict>
    </s:key>
    <s:key name="eai:acl">
      <s:dict>
        <s:key name="perms">
          <s:dict>
            <s:key name="read">
              <s:list>
                <s:item>admin</s:item>
              </s:list>
            </s:key>
            <s:key name="write">
              <s:list>
                <s:item>admin</s:item>
              </s:list>
            </s:key>
          </s:dict>
        </s:key>
        <s:key name="owner">admin</s:key>
        <s:key name="modifiable">1</s:key>
        <s:key name="sharing">global</s:key>
        <s:key name="app">search</s:key>
        <s:key name="can_write">1</s:key>
        <s:key name="ttl">600</s:key>
      </s:dict>
    </s:key>
    <s:key name="searchProviders">
      <s:list>
        <s:item>splunk.mycompany.invalid</s:item>
      </s:list>
    </s:key>
  </s:dict>
</content>
</entry>
  """
  
  @Test
  void should_parse_status() {
    def test = new SplunkTask()
    assert test.parseStatus(statusResp) == "DONE"
  }
  
  @Test
  void should_wait_for_sid() {
    long slept = 0
    def test = new SplunkTask() {
      def sendToSplunk(uri, content, memory) { return statusResp }
      def pause(long t) { slept += t; if (slept > 60000) { throw new Exception() } }
    }
    def props = new Properties()
    assert test.waitForSid("uri1", props)
    assert slept == 500
  }

  @Test
  void should_wait_for_sid_timeout() {
    long slept = 0
    def test = new SplunkTask() {
      def sendToSplunk(uri, content, memory) { }
      def pause(long t) { slept += t; if (slept > 60000) { throw new Exception() } }
    }
    def props = new Properties()
    try {
      test.waitForSid("uri1", props)
    } catch (Exception e) {
      assert e.class == Exception.class
    }
    assert slept >= 60000
  }
}

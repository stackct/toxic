
package toxic.http

import org.junit.*
import groovy.mock.interceptor.*
import org.junit.rules.ExpectedException
import toxic.ToxicProperties
import util.TimeoutException

public class HttpTaskTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  @Test
  public void testInitXmlFile() {
    def xt = new HttpTask()
    File f = new File("initXmlTest_req.http")
    f.createNewFile()
    xt.init(f, null)
    f.delete()
  }

  @Test
  public void testInitUnconformingXmlFile() {
    def xt = new HttpTask()
    File f = new File("initUnconformingXmlTest.http")
    f.createNewFile()
    try {
      xt.init(f, null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
    f.delete()
  }

  @Test
  public void testInitDir() {
    def xt = new HttpTask()
    try {
      xt.init(new File("test"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitMissingXmlFile() {
    def xt = new HttpTask()
    try {
      xt.init(new File("sdfsdf324"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitNotXmlFile() {
    def xt = new HttpTask()
    try {
      xt.init(new File("VERSION"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitString() {
    def xt = new HttpTask()
    xt.init("sdf", null)
  }

  @Test
  public void testHeaders() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["http.header.Test-This"] = "hello"
    props["http.header.Another"] = "world"
    props["http.header.Empty"] = ""
    props["http.header.Null"] = null
    xt.init(null, props)
    def expectedResult = "Another: world\r\nTest-This: hello\r\n"
    assert xt.headers() == expectedResult
  }

  @Test
  public void testPrepare() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["httpMethod"] = "POST / HTTP/1.1"
    props["http.header.Test-This"] = "hello"
    props["http.header.Another"] = "world"
    def content = """<hello>
  there
</hello>"""
    xt.init(content, props)
    def expectedResult = "POST / HTTP/1.1\r\nAnother: world\r\nTest-This: hello\r\nContent-Length: " + content.size() + "\r\n\r\n" + content
    def result = xt.prepare(content)
    assert result == expectedResult
  }

  @Test
  public void test_prepare_gzip() {
    def xt = new HttpTask()

    def props = new ToxicProperties()
    props["httpMethod"] = "POST / HTTP/1.1"
    props["http.header.Test-This"] = "hello"
    props["http.header.Another"] = "world"
    props["Content-Type"] = "gzip"

    def content = """<hello>
  there
</hello>"""
    xt.init(content, props)
    xt.gzip = true

    def gzippedContent = HttpTask.compress(content)

    def expectedResult = new ByteArrayOutputStream()
    expectedResult << "POST / HTTP/1.1\r\nAnother: world\r\nTest-This: hello\r\nContent-Length: "
    expectedResult << gzippedContent.size()
    expectedResult << "\r\n\r\n"
    expectedResult << gzippedContent

    def result = xt.prepare(content)
    assert result == expectedResult.toByteArray()
  }

  @Test
  public void testPrepareSkip() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["httpMethod"] = "POST / HTTP/1.1"
    props["http.header.Test-This"] = "hello"
    props["http.header.Another"] = "world"
    def content = "GET / HTTP/1.1\r\n\r\n"
    xt.init(content, props)
    def expectedResult = "GET / HTTP/1.1\r\n\r\n"
    def result = xt.prepare(content)
    assert result == expectedResult
  }

  @Test
  public void testReplace() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["task.replacer.1"]="toxic.groovy.GroovyReplacer"
    props["task.replacer.2"]="toxic.VariableReplacer"
    props.color = "red"
    props.item = "ball"
    props.testValue = 23
    def expectedResult = "This is a red ball with the number 123"
    def test = "This is a %color% %item% with the number `100+memory.testValue`"
    xt.init(null, props)
    def result = xt.replace(test)
    assert result == expectedResult
  }

  @Test
  public void testReplaceOutOfOrder() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["task.replacer.2"]="toxic.groovy.GroovyReplacer"
    props["task.replacer.1"]="toxic.VariableReplacer"
    props.color = "red"
    props.item = "ball"
    props.nested = 100
    props.testValue = 23
    def expectedResult = "This is a red ball with the number 123"
    def test = "This is a %color% %item% with the number `%nested%+memory.testValue`"
    xt.init(null, props)
    def result = xt.replace(test)
    assert result == expectedResult
  }

  @Test
  public void testReplaceOutOfOrderDependency() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["task.replacer.1"]="toxic.groovy.GroovyReplacer"
    props["task.replacer.2"]="toxic.VariableReplacer"
    props.color = "red"
    props.item = "ball"
    props.nested = 100
    props.testValue = 23
    def expectedResult = "This is a red ball with the number 10023"
    def test = "This is a %color% %item% with the number `\"%nested%\"+memory.testValue`"
    xt.init(null, props)
    def result = xt.replace(test)
    assert result == expectedResult
  }

  @Test
  public void testReplaceFail() {
    def xt = new HttpTask()
    def props = new ToxicProperties()
    props["task.replacer.1"]="toxic.foo.Bar"
    props.color = "red"
    props.item = "ball"
    props.testValue = 23
    def expectedResult = "This is a red ball with the number 123"
    def test = "This is a %color% %item% with the number `100+memory.testValue`"
    xt.init(null, props)
    try {
      xt.replace(test)
      assert false : "Expected ClassNotFoundException"
    } catch (ClassNotFoundException cnfe) {
    }
  }

  @Test
  public void testLookupExpectedResponse() {
    def xt = new HttpTask()
    File respf = new File("lookup_resp.http")
    respf.text="test"
    def result = xt.lookupExpectedResponse(new File("lookup_req.http"))
    respf.delete()
    assert result == "test"
  }

  @Test
  public void testLookupExpectedResponseMissing() {
    def xt = new HttpTask()
    assert xt.lookupExpectedResponse(new File("missing_req.http")) == null
  }

  @Test
  void should_read_body_with_lowercase_content_length() {
    String http = 'HTTP/1.1 200 OK\r\ncontent-length: 5\r\n\r\n<ok/>'
    assert http == new HttpTask().readHttpBody(new ByteArrayInputStream(http.getBytes()))
  }

  @Test
  void should_read_body_with_chunked_encoding() {
    String http = 'HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\n\r\n<ok/>\r\n\r\n0\r\n\r\n'
    def task = new HttpTask()
    task.props = new Properties()
    task.props.httpMaxChunkedSize="500"
    assert http == task.readHttpBody(new ByteArrayInputStream(http.getBytes()))
  }

  @Test
  void should_set_http_connection_properties_from_uri() {
    def testCases = [
      [name:'bad uri', uri:'foo', host:'default.invalid', port:9999, ssl:'invalid'],
      [name:'standard http', uri:'http://foo.com', host:'foo.com', port:80, ssl:'false'],
      [name:'standard https', uri:'https://foo.com', host:'foo.com', port:443, ssl:'true'],
      [name:'http on nonstandard port', uri:'http://foo.com:123', host:'foo.com', port:123, ssl:'false'],
      [name:'https on nonstandard port', uri:'https://foo.com:123', host:'foo.com', port:123, ssl:'true'],
    ]

    testCases.each { tc ->
      def t = new HttpTask()

      // Default values, intended to be overridden by a valid httpUri. If the httpUri is invalid,
      // the values will remain intact.
      t.props = [
        httpHost: 'default.invalid',
        httpPort: 9999,
        httpSsl: 'invalid'
      ] as ToxicProperties
      
      t.props['httpUri'] = tc.uri
      t.setupHttpConnection()

      assert t.props['httpHost'] == tc.host, "test case failed; name:'${tc.name}'; field:host; wanted:${tc.host}; got:${t.props['httpHost']}"
      assert t.props['httpPort'] == tc.port, "test case failed; name:'${tc.name}'; field:port"
      assert t.props['httpSsl'] == tc.ssl, "test case failed; name:'${tc.name}'; field:ssl"
    }
  }

  @Test
  public void should_parse_http_response() {
    def props = [httpHost:'http://foo.invalid', httpPort: 0] as ToxicProperties
   
    def testCases = [
      [
        name: 'empty response, no body',
        response: null, 
        expected: [ headers: [:], code:null, reason:null, body:null]
      ],
      [
        name: 'good response, no body',
        response: makeResponse(200, 'OK', null, ['foo: bar']), 
        expected: [ headers: [foo: 'bar'], code:'200', reason:'OK', body:null]
      ],
      [
        name: 'good response, body',
        response: makeResponse(200, 'OK', '{"foo":"bar"}', ['foo: bar']), 
        expected: [ headers: [foo: 'bar'], code:'200', reason:'OK', body:'{"foo":"bar"}']
      ],
      [
        name: 'bad response, no body',
        response: makeResponse(404, 'Not Found', null, null), 
        expected: [ headers: [:], code:'404', reason:'Not Found', body:null]
      ],
      [
        name: 'bad response, body',
        response: makeResponse(400, 'Bad Request', '{"foo":"bar"}', ['foo: bar']), 
        expected: [ headers: [:], code:'400', reason:'Bad Request', body:'{"foo":"bar"}']
      ],
    ]
    
    testCases.each { tc ->
      new HttpTask().with { task ->
        task.metaClass.getSocketFromProps = { p -> new FakeSocket(tc.response) }
        task.transmit("ok", "ok", props)
      }

      tc.expected.headers.each { k,v -> 
        assert props['http.response.headers'][k] == v, "scenario '${tc.name}' failed; header mismatch" 
      }
      assert tc.expected.code == props['http.response.code'], "scenario '${tc.name}' failed; response code mismatch"
      assert tc.expected.reason == props['http.response.reason'], "scenario '${tc.name}' failed; response reason mismatch"
      assert tc.expected.body == props['http.response.body'], "scenario '${tc.name}' failed; body mismatch"
    }
  }

  @Test
  void should_not_retry_when_task_retry_not_configured() {
    int transmitCount = 0
    def task = [ transmit:{ request, expectedResponse, m ->
      transmitCount++
      return 'SUCCESS'
    }] as HttpTask

    def memory = new ToxicProperties()
    task.props = memory
    task.input = new File('/foo')
    task.reqContent = ''

    task.doTask(memory)
    assert 1 == transmitCount
    assert 'SUCCESS' == memory.lastResponse
  }

  @Test
  void should_fail_when_retry_condition_is_not_a_closure() {
    expectedException.expect(IllegalArgumentException.class)
    expectedException.expectMessage("task.retry.condition is not a closure")

    def memory = new ToxicProperties()
    memory['task.retry.condition'] = 'THIS_IS_NOT_A_CLOSURE'

    def task = new HttpTask()
    task.props = memory
    task.input = new File('/foo')

    task.doTask(memory)
  }

  @Test
  void should_retry_transmit_based_on_condition() {
    int transmitCount = 0
    def task = [ transmit:{ request, expectedResponse, m ->
      transmitCount++
      return transmitCount == 3 ? 'SUCCESS' : 'FAIL'
    }] as HttpTask

    def memory = new ToxicProperties()
    memory['task.retry.condition'] = { response -> return 'SUCCESS' == response }
    memory['task.retry.every'] = 1
    memory['task.retry.atMostMs'] = 1000

    task.props = memory
    task.input = new File('/foo')
    task.reqContent = ''

    task.doTask(memory)
    assert 3 == transmitCount
    assert 'SUCCESS' == memory.lastResponse
  }

  @Test
  void should_fail_after_retries_are_exhausted() {
    expectedException.expect(TimeoutException.class)

    def task = [ transmit:{ request, expectedResponse, m ->
      return 'FAIL'
    }] as HttpTask

    def memory = new ToxicProperties()
    memory['task.retry.condition'] = { response -> return 'SUCCESS' == response }
    memory['task.retry.every'] = 1
    memory['task.retry.atMostMs'] = 1

    task.props = memory
    task.input = new File('/foo')
    task.reqContent = ''

    task.doTask(memory)
  }

  @Test
  void should_fail_when_wait_conditions_are_not_configured() {
    expectedException.expect(TimeoutException.class)

    def task = [ transmit:{ request, expectedResponse, m ->
      return 'FAIL'
    }] as HttpTask

    def memory = new ToxicProperties()
    memory['task.retry.condition'] = { response -> return 'SUCCESS' == response }

    task.props = memory
    task.input = new File('/foo')
    task.reqContent = ''

    task.doTask(memory)
  }

  private String makeResponse(int code, String reason, String body=null, List headers=[]) {
    def sb = new StringBuffer('HTTP/1.1 ' + code + ' ' + reason + '' + HttpTask.HTTP_CR)
    
    headers?.each { h -> sb.append(h + HttpTask.HTTP_CR) }
    
    if (body) {
      sb.append('Content-Length: ' + body.bytes.size() + HttpTask.HTTP_CR)
      sb.append(HttpTask.HTTP_CR)
      sb.append(body)
    }
    
    return sb.toString()
  }
}

public class FakeSocket extends Socket {
  private String response

  protected FakeSocket(String response) {
    this.response = response
  }
  
  @Override
  public boolean isClosed() {
    return false
  }

  @Override
  public boolean isConnected() {
    return true
  }

  @Override
  public void setSoTimeout(int n) {
  }

  @Override
  public InputStream getInputStream() {
    return new StringBufferInputStream(response ?: "")
  }

  @Override
  public OutputStream getOutputStream() {
    return new ByteArrayOutputStream()
  }
}

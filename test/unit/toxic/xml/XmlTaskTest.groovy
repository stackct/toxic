
package toxic.xml

import org.junit.*

import toxic.ToxicProperties

public class XmlTaskTest {
  @Test
  public void testInitXmlFile() {
    def xt = new XmlTask()
    File f = new File("initXmlTest_req.xml")
    f.createNewFile()
    xt.init(f, null)
    f.delete()
  }

  @Test
  public void testInitUnconformingXmlFile() {
    def xt = new XmlTask()
    File f = new File("initUnconformingXmlTest.xml")
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
    def xt = new XmlTask()
    try {
      xt.init(new File("test"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitMissingXmlFile() {
    def xt = new XmlTask()
    try {
      xt.init(new File("sdfsdf324"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitNotXmlFile() {
    def xt = new XmlTask()
    try {
      xt.init(new File("VERSION"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitString() {
    def xt = new XmlTask()
    xt.init("sdf", null)
  }

  @Test
  public void testHeaders() {
    def xt = new XmlTask()
    def props = new ToxicProperties()
    props["xml.header.Test-This"] = "hello"
    props["xml.header.Another"] = "world"
    props["xml.header.Empty"] = ""
    props["xml.header.Null"] = null
    xt.init(null, props)
    def expectedResult = "Another: world\r\nTest-This: hello\r\n"
    assert xt.headers() == expectedResult
  }

  @Test
  public void testPrepare() {
    def xt = new XmlTask()
    def props = new ToxicProperties()
    props["xmlMethod"] = "POST / HTTP/1.1"
    props["xml.header.Test-This"] = "hello"
    props["xml.header.Another"] = "world"
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
    def xt = new XmlTask()

    def props = new ToxicProperties()
    props["xmlMethod"] = "POST / HTTP/1.1"
    props["xml.header.Test-This"] = "hello"
    props["xml.header.Another"] = "world"
    props["Content-Type"] = "gzip"

    def content = """<hello>
  there
</hello>"""
    xt.init(content, props)
    xt.gzip = true

    def gzippedContent = XmlTask.compress(content)

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
    def xt = new XmlTask()
    def props = new ToxicProperties()
    props["xmlMethod"] = "POST / HTTP/1.1"
    props["xml.header.Test-This"] = "hello"
    props["xml.header.Another"] = "world"
    def content = "GET / HTTP/1.1\r\n\r\n"
    xt.init(content, props)
    def expectedResult = "GET / HTTP/1.1\r\n\r\n"
    def result = xt.prepare(content)
    assert result == expectedResult
  }

  @Test
  public void testReplace() {
    def xt = new XmlTask()
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
    def xt = new XmlTask()
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
    def xt = new XmlTask()
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
    def xt = new XmlTask()
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
    def xt = new XmlTask()
    File respf = new File("lookup_resp.xml")
    respf.text="test"
    def result = xt.lookupExpectedResponse(new File("lookup_req.xml"))
    respf.delete()
    assert result == "test"
  }

  @Test
  public void testLookupExpectedResponseMissing() {
    def xt = new XmlTask()
    assert xt.lookupExpectedResponse(new File("missing_req.xml")) == null
  }

  @Test
  void should_read_body_with_lowercase_content_length() {
    String xml = 'POST / HTTP/1.1\r\ncontent-length: 5\r\n\r\n<ok/>'
    assert xml == new XmlTask().readHttpBody(new ByteArrayInputStream(xml.getBytes()))
  }

/*
  @Test(groups=["connected"])
  public void testTransmit() {
    ToxicProperties tp = new ToxicProperties()
    tp.xmlHost="localhost"
    tp.xmlPort=22
    XmlTask xt = new XmlTask()
    xt.init(null, tp)
    def result = xt.transmit("test\n", tp)
    assert result.contains("SSH")
  }

  @Test(groups=["connected"])
  public void testTransmitStrPort() {
    ToxicProperties tp = new ToxicProperties()
    tp.xmlHost="localhost"
    tp.xmlPort="22"
    XmlTask xt = new XmlTask()
    xt.init(null, tp)
    def result = xt.transmit("test\n", tp)
    assert result.contains("SSH")
  }

  @Test(groups=["connected"])
  public void testTransmitSsl() {
    ToxicProperties tp = new ToxicProperties()
    tp.xmlHost="s3.amazonaws.com"
    tp.xmlPort="443"
    tp.xmlSsl="true"
    XmlTask xt = new XmlTask()
    xt.init(null, tp)
    def result = xt.transmit("GET /title.html HTTP/1.1\r\nHost: s3.amazonaws.com\r\nConnection: close\r\n\r\n", tp)
    assert result.contains("HTTP")
  }
  */
}
package toxic.websocket

import toxic.ToxicProperties
import toxic.ValidationException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class WebSocketTaskTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  @Test
  void should_validate_single_response() {
    def request = """{
                      "topic": "testTopic:v1",
                      "event": "testEvent",
                      "payload": {
                        "foo": "foo",
                        "bar": "%bar%",
                        "foobar": %foobar%
                      }
                    }"""
    def expectedResponses = """[
                                {
                                  "topic": "testTopic:v1",
                                  "event": "testEvent",
                                  "payload": {
                                    "bar": "%=bar%"
                                  }
                                }
                              ]"""
    def actualResponses = ["""{
                            "topic": "testTopic:v1",
                            "event": "testEvent",
                            "payload": {
                              "bar": "foobar"
                            }
                          }"""]

    def props = runWebSocketTask(request, expectedResponses, actualResponses, [bar: 'bar', foobar: '{ "foo": "bar" }'])
    assert 'foobar' == props.bar
  }

  @Test
  void should_fail_when_single_response_does_not_match() {
    expectedException.expect(ValidationException.class)
    expectedException.expectMessage('Content mismatch; path=/[0]/payload/bar; expected=foobar; actual=foobaz')

    def request = """{ "topic": "testTopic:v1", "event": "testEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    def expectedResponses = """[{ "topic": "testTopic:v1", "event": "testEvent",
                                  "payload": {
                                    "bar": "foobar"
                                  }
                                }]"""
    def actualResponses = ["""{ "topic": "testTopic:v1", "event": "testEvent",
                            "payload": {
                              "bar": "foobaz"
                            }
                          }"""]
    runWebSocketTask(request, expectedResponses, actualResponses)
  }

  @Test
  void should_assign_multiline_structure_to_variable() {
    def request = """{ "topic": "testTopic:v1", "event": "testEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    def expectedResponses = """[{ "topic": "testTopic:v1", "event": "testEvent",
                                  "payload": {
                                    "foo": "%=foo%",
                                    "bar": %=foobar%
                                  }
                                }]"""
    def actualResponses = ["""{ "topic": "testTopic:v1", "event": "testEvent",
                            "payload": {
                              "foo": "foo",
                              "bar": {
                                "foo": "bar"
                              }
                            }
                          }"""]
    def props = runWebSocketTask(request, expectedResponses, actualResponses)
    assert 'foo' == props.foo
    assert [foo: 'bar'] == props.foobar
  }

  @Test
  void should_validate_multiple_responses() {
    def request = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    def expectedResponses = """[
                                {
                                  "topic": "fooTopic",
                                  "event": "mainEvent",
                                  "payload": {
                                    "bar": "%=bar%"
                                  }
                                },
                                {
                                  "topic": "barTopic",
                                  "event": "barEvent",
                                  "payload": {
                                    "foobar": "%=foobar%"
                                  }
                                }
                              ]"""
    def actualResponses = ["""{
                            "topic": "fooTopic",
                            "event": "mainEvent",
                            "payload": {
                              "bar": "bar"
                            }
                          }""",
                           """{
                            "topic": "barTopic",
                            "event": "barEvent",
                            "payload": {
                              "foobar": "foobar"
                            }
                          }"""]

    def props = runWebSocketTask(request, expectedResponses, actualResponses)
    assert 'bar' == props.bar
    assert 'foobar' == props.foobar
  }

  @Test
  void should_throw_exception_when_response_is_not_received() {
    expectedException.expect(WebSocketTimeoutException.class)
    expectedException.expectMessage('Timed out while waiting for response')

    def request = """{
                      "topic": "testTopic:v1",
                      "event": "testEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    def expectedResponses = """[
                                {
                                  "topic": "testTopic:v1",
                                  "event": "testEvent",
                                  "payload": {
                                    "bar": "%=bar%"
                                  }
                                }
                              ]"""
    def actualResponses = []

    runWebSocketTask(request, expectedResponses, actualResponses, ['webSocketClientTimeoutMs': 0])
  }

  @Test
  void should_implement_a_no_op_transmit() {
    assert null == new WebSocketTask().transmit(null, null)
  }

  @Test
  void should_throw_exception_when_web_socket_client_endpoint_is_not_defined() {
    expectedException.expect(IllegalStateException.class)
    expectedException.expectMessage('Please configure either a webSocketClientUri or webSocketClientEndpoint property')
    runWebSocketTask('{}', '{}', [], [webSocketClientEndpoint:null])
  }

  @Test
  void should_resolve_client_endpoint() {
    assert new WebSocketTask().resolveWebSocketClientEndpoint([webSocketClientUri:'ws://localhost:4000/socket/websocket', webSocketClientType:'phoenix']) instanceof PhoenixWebSocketClientEndpoint
    assert new WebSocketTask().resolveWebSocketClientEndpoint([webSocketClientUri:'ws://localhost:4000/socket/websocket']) instanceof WebSocketClientEndpoint
  }

  @Test
  void should_default_latch_timeout_if_not_configured() {
    assert 1000 == new WebSocketTask().latchTimeoutMs([webSocketClientTimeoutMs:1000])
    assert 1000 == new WebSocketTask().latchTimeoutMs([webSocketClientTimeoutMs:1000L])
    assert 3000 == new WebSocketTask().latchTimeoutMs([webSocketClientTimeoutMs:null])
    assert 3000 == new WebSocketTask().latchTimeoutMs([webSocketClientTimeoutMs:''])
    assert 3000 == new WebSocketTask().latchTimeoutMs([webSocketClientTimeoutMs:'INVALID'])
    assert 3000 == new WebSocketTask().latchTimeoutMs([:])
  }

  @Test
  void should_parse_request() {
    def prepareRequest = { String reqContent, def props ->
      props = mockProps(props)
      def webSocketTask = new WebSocketTask(reqContent: reqContent, props: props)
      webSocketTask.parseRequestFile()
    }

    assert [foo: [fooKey: 'barValue']] == prepareRequest("""{ "foo": %bar% }""", [bar: [fooKey: 'barValue']])
    assert [foo: ['bar1', 'bar2', 'bar3']] == prepareRequest("""{ "foo": %bar% }""", [bar: ['bar1', 'bar2', 'bar3']])
    assert [foo: 'bar'] == prepareRequest("""{ "foo": "%bar%" }""", [bar: 'bar'])
    assert [foo: '%bar%'] == prepareRequest("""{ "foo": "%bar%" }""", [:])
  }

  def runWebSocketTask(String requestFileContents, String responseFileContents, def mockResponses, def customProps = [:]) {
    def endpoint = new WebSocketClientEndpoint('ws://localhost:4000/socket/websocket')
    endpoint.metaClass.connect = {}
    endpoint.metaClass.sendJsonMessage = { String topic, String event, Map payload ->
      mockResponses.each {
        endpoint.onMessage(it)
      }
    }

    customProps = ['webSocketClientEndpoint':endpoint, 'webSocketClientTimeoutMs': 1000] + customProps
    def props = mockProps(customProps)

    mockFiles(requestFileContents, responseFileContents) { requestFile, responseFile ->
      new WebSocketTask(input: requestFile, reqContent:requestFileContents, props: props).doTask(props)
    }
    props
  }

  def mockProps(customProps) {
    def props = new ToxicProperties()
    props.put('task.replacer.1', 'toxic.groovy.GroovyReplacer')
    props.put('task.replacer.2', 'toxic.VariableReplacer')
    customProps.each { k, v ->
      props.put(k, v)
    }
    props
  }

  def mockFiles(String request, String response, Closure c) {
    def requestFile
    def responseFile
    try {
      requestFile = File.createTempFile('WebSocketTaskTest', '_req.ws')
      requestFile.text = request

      responseFile = new File(requestFile.parent, requestFile.name.replace('_req', '_resp'))
      responseFile.text = response

      c(requestFile, responseFile)
    }
    finally {
      requestFile?.delete()
      responseFile?.delete()
    }
  }
}

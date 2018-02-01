package toxic.websocket

import groovy.json.JsonOutput
import org.apache.log4j.Level
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import static log.Log.LEVEL_DEBUG

import javax.websocket.RemoteEndpoint
import javax.websocket.Session
import javax.websocket.WebSocketContainer

class WebSocketClientEndpointTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  def logLevel
  String uri = 'ws://localhost:4000'

  @Before
  void before() {
    logLevel = WebSocketClientEndpoint.log.getLevel()
    WebSocketClientEndpoint.log.setLevel(LEVEL_DEBUG)
  }

  @After
  void after() {
    WebSocketClientEndpoint.log.setLevel(logLevel)
  }

  @Test
  void test_construction() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    assert uri == endpoint.endpointURI.toString()
    assert null != endpoint.messageHandler
  }

  @Test
  void test_get_container_provider() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    assert endpoint.getContainerProvider() instanceof WebSocketContainer
  }

  @Test
  void test_connect() {
    def actualAnnotatedEndpointInstance
    def actualPath
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.metaClass.getContainerProvider = { -> [
      connectToServer: { def annotatedEndpointInstance, URI path ->
        actualAnnotatedEndpointInstance = annotatedEndpointInstance
        actualPath = path
      }
    ]}
    endpoint.connect()
    assert actualAnnotatedEndpointInstance instanceof WebSocketClientEndpoint
    assert uri == actualPath.toString()
  }

  @Test
  void test_close() {
    boolean closed = false
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    Session session = [close:{ closed = true}] as Session
    endpoint.onOpen(session)
    assert session == endpoint.userSession
    endpoint.close()
    assert closed
  }

  @Test
  void test_on_open() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    Session session = [:] as Session
    endpoint.log.track { logger ->
      endpoint.onOpen(session)
      assert session == endpoint.userSession
      assert logger.isLogged('opening websocket', Level.DEBUG)
    }
  }

  @Test
  void test_on_close() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    Session session = [:] as Session
    endpoint.log.track { logger ->
      endpoint.onOpen(session)
      assert session == endpoint.userSession
      endpoint.onClose(null, null)
      assert null == endpoint.userSession
      assert logger.isLogged('closing websocket', Level.DEBUG)
    }
  }

  @Test
  void test_on_message() {
    String expectedMessage = "TEST PAYLOAD"
    String actualMessage
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.messageHandler = null
    endpoint.onMessage(expectedMessage)
    assert null == actualMessage
    endpoint.messageHandler = { String message ->
      actualMessage = message
    }
    endpoint.onMessage(expectedMessage)
    assert expectedMessage == actualMessage
  }

  @Test
  void test_send_message() {
    String expectedMessage = "TEST PAYLOAD"
    String actualMessage
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.metaClass.remoteEndpoint = {
      [sendText:{message -> actualMessage = message}]
    }
    endpoint.sendMessage(expectedMessage)
    assert expectedMessage == actualMessage
  }

  @Test
  void test_remote_endpoint() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    Session session = [getAsyncRemote:{[:] as RemoteEndpoint.Async}] as Session
    endpoint.onOpen(session)
    assert endpoint.remoteEndpoint() instanceof RemoteEndpoint.Async
  }

  @Test
  void test_json_event() {
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    String expectedPayload = JsonOutput.toJson(['key':'value'])
    Map message = [topic: 'room:lobby', event: 'test_event', payload: expectedPayload]
    String actualPayload
    endpoint.addEventHandler('room:lobby', 'test_event') { String payload -> actualPayload = payload }
    endpoint.onMessage(JsonOutput.toJson(message))
    assert expectedPayload == actualPayload
  }

  @Test
  void should_send_json_message() {
    String actualMessage
    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.metaClass.sendMessage = { String message ->
      actualMessage = message
    }
    endpoint.sendJsonMessage('room:lobby', 'test_event', [foo: 'bar'])
    assert '{"topic":"room:lobby","event":"test_event","payload":"{\\"foo\\":\\"bar\\"}"}' == actualMessage
  }

  @Test
  void should_fail_if_dupliate_event_handlers_are_configured_for_the_same_topic() {
    expectedException.expect(IllegalStateException.class)
    expectedException.expectMessage('Duplicate event handlers defined for same topic; topic=topic1; event=event1')

    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.addEventHandler('topic1', 'event1', {})
    endpoint.addEventHandler('topic1', 'event1', {})
  }

  @Test
  void should_fail_if_no_event_handler_is_found_for_response() {
    expectedException.expect(UnexpectedResponseException.class)
    expectedException.expectMessage('No response handler defined for event; topic=topic1; event=event1; payload={payload}')

    WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(uri)
    endpoint.handleResponse('topic1', 'event1', '{payload}')
  }
}

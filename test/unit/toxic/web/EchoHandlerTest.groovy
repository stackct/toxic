package toxic.web

import org.junit.*

import log.Log
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*
import java.util.concurrent.*
import spark.*

public class EchoHandlerTest {

  @After
  public void after() {
    Thread.metaClass = null
  }

  @Test
  public void should_start_daemon_thread() {
    boolean threadStarted = false
    String threadName = ""

    Thread.metaClass.'static'.startDaemon = { String name, Closure c -> 
      threadStarted = true 
      threadName = name
    }

    def handler = new EchoHandler().start()

    assert handler instanceof EchoHandler
    assert threadStarted
    assert threadName == "ws-echo"
  }

  @Test
  public void should_send_message_to_all_connected_sessions() {
    def handler = new EchoHandler()

    def messagesReceived = [:]
    messagesReceived["a"] = []
    messagesReceived["b"] = []
    messagesReceived["c"] = []
    
    def sessions = [:]
    sessions["a"] = makeSession { s -> messagesReceived["a"] << s }
    sessions["b"] = makeSession { s -> messagesReceived["b"] << s }
    sessions["c"] = makeSession { s -> messagesReceived["c"] << s }

    sessions.each { k,v -> handler.connected(v) }

    assert messagesReceived['a'] == ['{"connected":true}']
    assert messagesReceived['b'] == ['{"connected":true}']
    assert messagesReceived['c'] == ['{"connected":true}']

    handler.pulse()

    assert messagesReceived['a'] == ['{"connected":true}','{"connected":true}']
    assert messagesReceived['b'] == ['{"connected":true}','{"connected":true}']
    assert messagesReceived['c'] == ['{"connected":true}','{"connected":true}']

    handler.closed(sessions["a"], 0, null)
    handler.pulse()

    assert messagesReceived['a'] == ['{"connected":true}', '{"connected":true}']
    assert messagesReceived['b'] == ['{"connected":true}', '{"connected":true}', '{"connected":true}']
    assert messagesReceived['c'] == ['{"connected":true}', '{"connected":true}', '{"connected":true}']
  }

  private makeSession(Closure c) {
    def remoteEndpoint = [sendString: { s -> c(s) }] as RemoteEndpoint
    def session = [getRemote: { -> remoteEndpoint }] as Session

    return session
  }
}

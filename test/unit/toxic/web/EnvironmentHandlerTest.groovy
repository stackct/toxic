package toxic.web

import org.junit.*

import log.Log
import toxic.Environment
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*
import java.util.concurrent.*
import spark.*

public class EnvironmentHandlerTest {

  @After
  public void after() {
    Thread.metaClass = null
    Environment.instance.metaClass = null
  }

  @Test
  public void should_start_daemon_thread() {
    boolean threadStarted = false
    String threadName = ""

    Thread.metaClass.'static'.startDaemon = { String name, Closure c -> 
      threadStarted = true 
      threadName = name
    }

    def handler = new EnvironmentHandler().start()

    assert handler instanceof EnvironmentHandler
    assert threadStarted
    assert threadName == "ws-environment"
  }

  @Test
  public void should_send_message_to_all_connected_sessions() {

    Environment.instance.metaClass.toSimple = { -> [foo:"bar"] }

    def handler = new EnvironmentHandler()

    def messagesReceived = [:]
    messagesReceived["a"] = []
    messagesReceived["b"] = []
    messagesReceived["c"] = []
    
    def sessions = [:]
    sessions["a"] = makeSession { s -> messagesReceived["a"] << s }
    sessions["b"] = makeSession { s -> messagesReceived["b"] << s }
    sessions["c"] = makeSession { s -> messagesReceived["c"] << s }

    sessions.each { k,v -> handler.connected(v) }

    def data = '{"foo":"bar","shutdownPending":false,"runningJobCount":0,"jobMetrics":null,"notifications":null}'
    assert messagesReceived['a'] == [data]
    assert messagesReceived['b'] == [data]
    assert messagesReceived['c'] == [data]

    handler.pulse()

    assert messagesReceived['a'] == [data, data]
    assert messagesReceived['b'] == [data, data]
    assert messagesReceived['c'] == [data, data]

    handler.closed(sessions["a"], 0, null)
    handler.pulse()

    assert messagesReceived['a'] == [data, data]
    assert messagesReceived['b'] == [data, data, data]
    assert messagesReceived['c'] == [data, data, data]
  }

  private makeSession(Closure c) {
    def remoteEndpoint = [sendString: { s -> c(s) }] as RemoteEndpoint
    def session = [
      getRemote: { -> remoteEndpoint },
      setIdleTimeout: { long m -> }
    ] as Session

    return session
  }
}

package toxic.web

import log.Log
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*
import java.util.concurrent.*
import groovy.json.*
import spark.*

@WebSocket
public class EchoHandler {
  private static final Log log = Log.getLogger(this)
  private static final int REFRESH_INTERVAL = 5000
  private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>()

  public EchoHandler start() {
    Thread.startDaemon("ws-echo") { 
      while(true) {
        pulse(); Thread.sleep(REFRESH_INTERVAL)
      }
    }

    return this
  }

  protected void pulse() {
    sessions.each { s -> sendMessage(s) }
  }

  private void sendMessage(Session s) {
    def message = new JsonBuilder([connected:true]).toString()

    try {
      s.getRemote().sendString(message)
    } catch(IOException | WebSocketException ex) {
      log.debug("Could not send message to remote endpoint; reason=${ex.message}")
    }
  }

  @OnWebSocketConnect
  public void connected(Session session) {
    sessions.add(session)
    sendMessage(session)
  }

  @OnWebSocketClose
  public void closed(Session session, int statusCode, String reason) {
    sessions.remove(session)
  }
}

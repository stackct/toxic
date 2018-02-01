package toxic.web


import log.Log
import toxic.job.*
import toxic.Environment
import toxic.notification.*
import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*
import java.util.concurrent.*
import groovy.json.*
import spark.*

@WebSocket
public class EnvironmentHandler {
  private static final Log log = Log.getLogger(this)
  private static final int REFRESH_INTERVAL = 10000
  private static final int DAYS_OF_HISTORY = 7
  private static final List sessions = []

  JobManager jobManager
  EventCollector eventCollector

  public EnvironmentHandler start() {
    Thread.startDaemon("ws-environment") { 
      while(true) {
        pulse(); Thread.sleep(REFRESH_INTERVAL)
      }
    }

    return this
  }

  protected synchronized void pulse() {
    sessions.each { s -> sendMessage(s) }
  }

  private void sendMessage(Session s) {
    def data = Environment.instance.toSimple()
    data.shutdownPending = jobManager?.isShuttingDown() ?: false
    data.runningJobCount = jobManager?.runningJobs?.size() ?: 0
    data.jobMetrics = jobManager?.generateMetrics(new Date() - DAYS_OF_HISTORY)
    data.notifications = eventCollector?.all()
    
    def message

    try {
      message = new JsonBuilder(data).toString()
    } catch(IllegalArgumentException ex) {
      log.warn("Could not build message; reason=${ex.message}; data=${data}")
    }
    
    if (message) {
      try {
        s.getRemote().sendString(message)
      } catch(IOException | WebSocketException ex) {
        log.debug("Could not send message to remote endpoint; reason=${ex.message}")
      }
    }
  }

  @OnWebSocketConnect
  public synchronized void connected(Session session) {
    session.setIdleTimeout(300000)
    sessions.add(session)
    sendMessage(session)
  }

  @OnWebSocketClose
  public synchronized void closed(Session session, int statusCode, String reason) {
    sessions.remove(session)
  }
}

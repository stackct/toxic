package toxic.web

import toxic.job.*
import toxic.notification.*
import log.Log

import org.eclipse.jetty.websocket.api.*
import org.eclipse.jetty.websocket.api.annotations.*
import java.util.concurrent.*
import groovy.json.*
import spark.*

@WebSocket
public class ProjectHandler implements Subscriber {
  private static Log log = Log.getLogger(this)
  private static List sessions
  private static Map jobs
 
  static {
    reset()
  }

  static reset() {
    sessions = []
    jobs = [:]
  }

  private JsonSlurper jsonSlurper

  public ProjectHandler() {
    this.jsonSlurper = new JsonSlurper()
    
    def events = [
      EventType.JOBS_LOADED, 
      EventType.JOB_ACKED,
      EventType.JOB_RESOLVED,
      EventType.JOB_CHANGED, 
      EventType.PROJECT_PAUSED, 
      EventType.PROJECT_UNPAUSED
    ]
    
    NotificationCenter.instance.subscribe(this, events)

    Thread.startDaemon("ws-project") {
      while(true) {
        log.debug("Session Summary: sessions:${sessions.size()}; jobsCached=${jobs.size()}")

        Thread.sleep(10000)
      }
    }
  }

  public synchronized void handle(Notification n) {
    log.debug("Received notification; type=${n.type}")

    switch(n.type) {
      case EventType.JOB_ACKED:
      case EventType.JOB_RESOLVED:
        def job = jobs.find { k,v -> v.job.id == n.data.id }?.value?.job
        if (job) {
          job.acked = n.data.acked
        }
        break
      case EventType.JOBS_LOADED:
        if (!jobs) cacheJobs(n.data.projects)
        break
      case EventType.JOB_CHANGED:
        def p = jobs[n.data.project]
        if (!p) {
          p = [:]
          jobs[n.data.project] = p
        }
        if (!p?.notification || n > p?.notification) {
          p.job = n.data
        }
        p.notification = n
        break
      case EventType.PROJECT_PAUSED:
      case EventType.PROJECT_UNPAUSED:
        jobs.each { p,j ->
          if (p == n.data.project) {
            j.job.paused = n.data.paused
          }
        }
        break
    }

    pulse()
  }

  private void cacheJobs(projectJobs) {
    projectJobs.each { j -> jobs[j.project] = [job:j, notification:null] }
    log.info("Cached ${jobs.size()} jobs")
  }

  private void pulse() {
    def data = buildJobData()

    def threads = []

    sessions.each { session -> 
      new Thread(new ProjectNotificationHandler(session, data)).with { t ->
        t.setDaemon(true)
        t.start()
        threads << t
      }
    }

    threads.each { t ->
      int retries = 0

      while (retries < 3) {
        try { 
          t.join(1000)
        } catch (InterruptedException ex) {
          log.debug("Timed out while waiting for thread to send message; reason=${ex.message}; retries=${retries}")
        } finally {
          retries++
        }
      }
    }
  }

  private String buildJobData() {
    new JsonBuilder(jobs.collect { p,j -> j.job }).toString()
  }

  @OnWebSocketConnect
  public synchronized void connected(Session session) {
    session.setIdleTimeout(300000)
    log.debug("Session connected; remote=${session.remoteAddress}; idleTimeoutMs=${session.idleTimeout}")
    sessions.add(session)
  }

  @OnWebSocketMessage
  public void message(Session session, String message) {
    log.debug("Received '${message}' from '${session.remoteAddress}'.")

    new ProjectNotificationHandler(session, buildJobData()).run()
  }

  @OnWebSocketClose
  public synchronized void closed(Session session, int statusCode, String reason) {
    log.debug("Session closed; remoteAddress=${session.remoteAddress}; statusCode=${statusCode}; reason=${reason}")
    sessions.remove(session)
  }
}

class ProjectNotificationHandler implements Runnable {
  private static Log log = Log.getLogger(this)

  private Session session
  private String message

  public ProjectNotificationHandler(Session s, String m) {
    this.session = s
    this.message = m
  }

  public void run() {
    try {
      session.remote.sendString(message)
    } catch (Exception ex) {
      log.debug("Could not send data to session; session=${session.remoteAddress}; e=${ex}; reason=${ex.message}")
      
      // Forcibly close the session to force the client to reconnect and get a fresh
      // copy of the data.
      if (session.isOpen()) {
        session.close()
      }
    }        
  }
}

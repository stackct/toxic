package toxic.web

import toxic.job.*
import toxic.notification.*

import spark.*
import groovy.json.*
import org.junit.*
import org.eclipse.jetty.websocket.api.*
import java.net.InetSocketAddress
import java.util.concurrent.*

public class ProjectHandlerTest {

  @Before
  public void before() {
    ProjectHandler.reset()
  }

  @After
  public void after() {
    Thread.metaClass = null
    ProjectHandler.reset()
  }

  @Test
  public void should_start_daemon_thread() {
    boolean threadStarted = false
    String threadName = ""

    Thread.metaClass.'static'.startDaemon = { String name, Closure c -> 
      threadStarted = true 
      threadName = name
    }

    def handler = new ProjectHandler()

    assert handler instanceof ProjectHandler
    assert threadStarted
    assert threadName == "ws-project"
  }

  @Test
  public void should_cache_jobs_on_jobs_loaded() {
    def handler = new ProjectHandler()

    def jobs = [ job('foo-1').toSimple(), job('bar-1').toSimple() ]

    handler.handle(new Notification(EventType.JOBS_LOADED, [projects:jobs]))
    assert handler.jobs.collect { p,j -> j.job.id }.sort() == ['foo-1', 'bar-1'].sort()

    handler.handle(new Notification(EventType.JOBS_LOADED, [projects:jobs]))
    assert handler.jobs.collect { p,j -> j.job.id }.sort() == ['foo-1', 'bar-1'].sort()
  }

  @Test
  public void should_push_project_list_on_message_received() {
    def handler = new ProjectHandler()

    handler.cacheJobs([[id:'foo-1', project:'foo'],[id:'bar-1', project:'bar']])

    def messagesReceived = [:]
    messagesReceived["a"] = []
    messagesReceived["b"] = []
    messagesReceived["c"] = []
    
    def sessions = [:]
    sessions["a"] = makeSession { s -> messagesReceived["a"] << s }
    sessions["b"] = makeSession { s -> messagesReceived["b"] << s }
    sessions["c"] = makeSession { s -> messagesReceived["c"] << s }

    sessions.each { k,v -> 
      handler.connected(v)
      handler.message(v, 'init')
    }

    assert messagesReceived['a'] == ['[{"id":"foo-1","project":"foo"},{"id":"bar-1","project":"bar"}]']
    assert messagesReceived['b'] == ['[{"id":"foo-1","project":"foo"},{"id":"bar-1","project":"bar"}]']
    assert messagesReceived['c'] == ['[{"id":"foo-1","project":"foo"},{"id":"bar-1","project":"bar"}]']
  }

  @Test
  public void should_resume_sending_to_active_sessions() {
    def handler = new ProjectHandler()

    handler.cacheJobs([[id:'foo-1', project:'foo']])

    def messagesReceived = [:]
    messagesReceived["a"] = []
    messagesReceived["b"] = []
    messagesReceived["c"] = []
    messagesReceived["d"] = []

    def sessions = [:]
    sessions["a"] = makeSession { s -> throw new WebSocketException() }
    sessions["b"] = makeSession { s -> messagesReceived["b"] << s }
    sessions["c"] = makeSession { s -> throw new IllegalStateException() }
    sessions["d"] = makeSession { s -> messagesReceived["d"] << s }

    sessions.each { k,v -> handler.connected(v) }

    handler.pulse()

    assert messagesReceived['a'] == []
    assert messagesReceived['b'] == ['[{"id":"foo-1","project":"foo"}]']
    assert messagesReceived['c'] == []
    assert messagesReceived['d'] == ['[{"id":"foo-1","project":"foo"}]']
  }

  @Test
  public void should_push_project_list_on_job_changed() {
    def handler = new ProjectHandler()

    addJob(handler, [id:'foo-1'])
    addJob(handler, [id:'bar-1'])

    handler.handle(new Notification(EventType.JOB_CHANGED, [id:'foo-2', project:'foo']))

    assert handler.jobs.collect { p,j -> j.job.id }.sort() == ['bar-1', 'foo-2'].sort()
  }

  @Test
  public void should_update_acked_job() {
    def handler = new ProjectHandler()
    def ackUser = [id:'user']

    addJob(handler, [id:'foo-1', acked:null])
    addJob(handler, [id:'bar-1', acked:null])

    handler.handle(new Notification(EventType.JOB_ACKED, [id:'foo-1', acked:ackUser]))

    assert findJob(handler, 'foo-1').acked == ackUser
    assert findJob(handler, 'bar-1').acked == null
  }

  @Test
  public void should_update_resolved_job() {
    def handler = new ProjectHandler()
    def ackUser = [id:'user']

    addJob(handler, [id:'foo-1', acked:'fred'])
    addJob(handler, [id:'bar-1', acked:null])
    
    handler.handle(new Notification(EventType.JOB_RESOLVED, [id:'foo-1', acked:null]))

    assert findJob(handler, 'foo-1').acked == null
    assert findJob(handler, 'bar-1').acked == null
  }

  @Test
  public void should_update_paused_projects() {
    def handler = new ProjectHandler()

    addJob(handler, [id:'foo-1', project:'foo'])
    addJob(handler, [id:'bar-1', project:'bar'])

    handler.handle(new Notification(EventType.PROJECT_PAUSED, [project:'foo', paused:true]))
    handler.handle(new Notification(EventType.PROJECT_UNPAUSED, [project:'bar', paused:false]))

    assert findJob(handler, 'foo-1').paused
    assert findJob(handler, 'bar-1').paused == false
  }

  @Test
  public void should_add_project_if_not_already_cached() {
    def handler = new ProjectHandler()

    addJob(handler, [id:'foo-1', project:'foo', status:'PENDING'])
    addJob(handler, [id:'bar-1', project:'bar', status:'RUNNING'])

    def n1 = new Notification(EventType.JOB_CHANGED, [id:'baz-1', project:'baz', status:'RUNNING'])

    handler.handle(n1)

    assert findJob(handler, 'baz-1').status == 'RUNNING'
  }

  @Test
  public void should_not_update_project_if_notification_is_stale() {
    def handler = new ProjectHandler()

    addJob(handler, [id:'foo-1', project:'foo', status:'PENDING'])
    addJob(handler, [id:'bar-1', project:'bar', status:'RUNNING'])

    def n1 = new Notification(EventType.JOB_CHANGED, [id:'foo-1', project:'foo', status:'ENDING'])
    def n2 = new Notification(EventType.JOB_CHANGED, [id:'foo-1', project:'foo', status:'COMPLETED'])

    handler.handle(n2)
    handler.handle(n1)

    assert findJob(handler, 'foo-1').status == 'COMPLETED'
    assert findJob(handler, 'bar-1').status == 'RUNNING'
  }

  @Test
  public void should_clean_up_sessions_on_disconnect() {
    def handler = new ProjectHandler()

    def sessions = [:]
    sessions["a"] = makeSession { s -> }
    sessions["b"] = makeSession { s -> }
    sessions["c"] = makeSession { s -> }

    handler.connected(sessions["a"])
    handler.connected(sessions["b"])
    handler.connected(sessions["c"])

    assert handler.sessions.collect { it } == [sessions["a"], sessions["b"], sessions["c"]]
    
    handler.closed(sessions["a"], 0, null)
    handler.closed(sessions["b"], 0, null)

    assert handler.sessions.collect { it } == [sessions["c"]]
  }

  private makeSession(Closure sendHandler) {
    makeSession("0.0.0.0", false, sendHandler)
  }

  private makeSession(String remoteAddress, boolean open, Closure sendHandler) {
    def remoteEndpoint = [sendString: { s -> sendHandler(s) }] as RemoteEndpoint

    def session = [:]
    session.isOpen = { -> open }
    session.setIdleTimeout = { long m -> }
    session.getRemoteAddress = { -> new InetSocketAddress(remoteAddress, 0) }
    session.getIdleTimeout = { -> 0L }
    session.getRemote = { -> remoteEndpoint }

    return session as org.eclipse.jetty.websocket.api.Session
  }

  private addJob(handler, job) {
    handler.jobs[proj(job)] = [job:job, notification:null]
  }

  private findJob(handler, id) {
    handler.jobs.find { k,v -> v.job.id == id }.value.job
  }

  private job(id) {
    new Job(id, null, null, null)
  }

  private proj(j) {
    job(j.id).project
  }
}

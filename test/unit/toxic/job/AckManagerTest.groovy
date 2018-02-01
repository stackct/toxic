package toxic.job

import org.junit.*
import groovy.mock.interceptor.*
import toxic.notification.*
import toxic.user.*

@Mixin([UserManagerTestMixin, NotificationCenterTestMixin]) 
public class AckManagerTest {

  def jobManager
  def savedData = [:]
  def notifications = [:]
  def subscriber
  def mockJobs

  @Before
  public void before() {
    resetNotifications()

    mockJobs = [job('foo'), job('bar'), job('baz'), job('failed.job-1')]
    
    jobManager = new JobManager("url") { 
      String getConfigDir() { return "/dev/null" } 
      List getJobs() { return mockJobs }
      List allJobs() { return mockJobs }
    }

    def events = [EventType.JOB_ACKED, EventType.JOB_RESOLVED]
    notifications = [:]
    events.each { e -> notifications[e] = [] }
    subscriber = [handle: { n -> notifications[n.type] << n }] as Subscriber
    NotificationCenter.instance.subscribe(subscriber, events)

    ConfigManager.instance.metaClass.read  = { JobManager mgr, String name -> savedData[name] }
    ConfigManager.instance.metaClass.write = { JobManager mgr, String name, Map data -> savedData[name] = data }

    userManagerSetup()
  }

  @After
  public void after() {
    AckManager.instance.reset()
    ConfigManager.instance.metaClass = null
    userManagerTeardown()
    resetNotifications()
  }

  @Test
  public void should_fail_if_job_not_found() {
    assert false == AckManager.instance.ack(jobManager, 'NOT_EXISTS', user('fred'))
  }

  @Test
  public void should_fail_if_user_not_found() {
    mockUsers = []
    assert false == AckManager.instance.ack(jobManager, 'failed.job-1', user('UNKNOWN'))
  }

  @Test
  public void should_not_ack_if_job_is_successful() {
    mockJobs = [job('failed.job-1', true)]

    assert false == AckManager.instance.ack(jobManager, 'failed.job-1', user('fred'))
    assert [:] == AckManager.instance.ackedJobs 
  }

  @Test
  public void should_ack_job() {
    mockJobs = [job('failed.job-1', false)]

    assert true == AckManager.instance.ack(jobManager, 'failed.job-1', user('fred'))
    assert ['failed.job-1':user('fred')] == AckManager.instance.ackedJobs 
  }

  @Test
  public void should_ack_latest_job_if_project_name_is_specified() {
    mockJobs = [
      job('failed.job-1', false),
      job("failed.job-2", false)
    ]
    mockJobs.each { it.currentStatus = JobStatus.COMPLETED }

    assert true == AckManager.instance.ack(jobManager, 'failed', user('fred'))
    assert ['failed.job-2':user('fred')] == AckManager.instance.ackedJobs 
  }

  @Test
  public void should_get_ack() {
    AckManager.instance.ack(jobManager, 'foo', user('fred'))
    AckManager.instance.ack(jobManager, 'bar', user('barney'))
    AckManager.instance.ack(jobManager, 'baz', user('wilma'))

    assert AckManager.instance.getAck('foo') == user('fred')
    assert AckManager.instance.getAck('bar') == user('barney')
    assert AckManager.instance.getAck('baz') == user('wilma')
    assert AckManager.instance.getAck('UNKNOWN') == null
  }

  @Test
  public void should_not_overwrite_entry_if_job_already_acked() {
    assert true == AckManager.instance.ack(jobManager, 'foo', user('fred'))
    assert false == AckManager.instance.ack(jobManager, 'foo', 'bob')
    assert AckManager.instance.ackedJobs == ['foo':user('fred')]
  }

  @Test
  public void should_not_resolve_job_if_user_is_different_from_ack() {
    assert true == AckManager.instance.ack(jobManager, 'foo', user('fred'))
    assert false == AckManager.instance.resolve(jobManager, 'foo', user('barney'))
    assert true == AckManager.instance.resolve(jobManager, 'foo', user('fred'))
    assert AckManager.instance.ackedJobs == [:]
  }

  @Test
  public void should_resolve_job() {
    mockJobs = [job('foo.job-1'), job('bar.job-1'), job('baz.job-1'), job('failed.job-1')]
    mockJobs.each { it.currentStatus = JobStatus.COMPLETED }
    assert false == AckManager.instance.resolve(jobManager, 'foo', user('fred'))
    assert true == AckManager.instance.ack(jobManager, 'foo', user('fred'))
    PauseManager.instance.pauseProject(jobManager, 'foo')
    assert PauseManager.instance.isProjectPaused('foo')
    assert true == AckManager.instance.resolve(jobManager, 'foo.job-1', user('fred'))
    assert !PauseManager.instance.isProjectPaused('foo')
    assert AckManager.instance.ackedJobs == [:]
  }

  @Test
  public void should_save_config_data() {
    AckManager.instance.ack(jobManager, 'foo', user('fred'))
    assert savedData['AckManager'] == [foo:user('fred')]
  }

  @Test
  public void should_load_config_data() {
    savedData[AckManager.class.simpleName] = ['foo':'bar']
    AckManager.instance.load(jobManager)
    assert AckManager.instance.ackedJobs == ['foo':'bar']
  }

  @Test
  public void should_notify_when_job_acked_and_cleared() {
    AckManager.instance.ack(jobManager, 'failed.job-1', user('fred'))
    AckManager.instance.resolve(jobManager, 'failed.job-1', user('fred'))

    stopNotifications()

    assert notifications[EventType.JOB_ACKED][0].data.id == 'failed.job-1'
    assert notifications[EventType.JOB_ACKED][0].data.acked == user('fred')
    assert notifications[EventType.JOB_RESOLVED][0].data.id == 'failed.job-1'
    assert notifications[EventType.JOB_RESOLVED][0].data.acked == null
  }

  @Test
  public void should_not_notify_when_job_ack_failed() {
    AckManager.instance.ack(jobManager, 'NOT.EXISTS.job', 'fred')
    AckManager.instance.resolve(jobManager, 'NOT.EXISTS.job', 'fred')

    stopNotifications()

    assert notifications[EventType.JOB_ACKED] == []
    assert notifications[EventType.JOB_RESOLVED] == []
  }

  private Job job(id, successful=false) {
    new Job(id:id, failed:successful ? 0 : 1)
  }
}

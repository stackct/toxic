package toxic.job

import org.junit.*
import java.nio.file.*
import java.text.*
import java.util.concurrent.*
import org.apache.commons.io.*
import toxic.*
import toxic.junit.*
import toxic.notification.*
import groovy.mock.interceptor.*
import toxic.groovy.*
import org.apache.log4j.*
import com.splunk.logging.log4j.appender.*

@Mixin(NotificationCenterTestMixin)
public class JobTest {

  def df = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.SSS')
  def tmpFile
  def date
  def subscriber
  def notifications

  @Before
  void before() {
    resetNotifications()
    tmpFile = File.createTempFile("tmp.throwaway", this.class.name)
    date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-12-05 07:16:12.123")
    EventManager.metaClass.getNow = { -> return date }

    def events = [EventType.JOBS_LOADED, EventType.JOB_CHANGED]
    notifications = [:]
    events.each { e -> notifications[e] = [] }
    subscriber = [handle: { n -> notifications[n.type] << n }] as Subscriber
    NotificationCenter.instance.subscribe(subscriber, events)
  }

  @After
  void after() {
    SourceRepositoryFactory.metaClass = null
    FileUtils.metaClass = null
    GroovyEvaluator.metaClass = null
    Calendar.metaClass = null
    File.metaClass = null
    tmpFile.delete()
    new File("jobDir").deleteDir()
    new File("projectDir").deleteDir()
    new File("null").deleteDir()
    EventManager.instance.destroy()
    EventManager.metaClass = null
    PauseManager.instance.reset()
    AckManager.instance.reset()
    PauseManager.metaClass = null
    Job.metaClass = null
    resetNotifications()
  }

  private mockJob() {
    mockJob(null,null,null,null)
  }

  private mockJob(submit, start, complete, artifacts = []) {
    def job = new Job("foo-bar-0", new File("jobDir"), new File("projectDir"), "details")

    job.submittedDate = submit
    job.startedDate = start
    job.completedDate = complete
    job.artifactsDir = new File(job.jobDir, "artifacts")
    job.properties['job.logFile'] = "testnothinghere"
    job.properties['job.logLevel'] = ""

    job.results = [
      new TaskResult([id:'1', family:'foo', name:'a', type:'type', success:true,  startTime:1,  stopTime:2]),
      new TaskResult([id:'2', family:'foo', name:'b', type:'type', success:false, startTime:3,  stopTime:5]),
      new TaskResult([id:'3', family:'foo', name:'c', type:'type', success:false, startTime:6,  stopTime:7]),
      new TaskResult([id:'4', family:'bar', name:'d', type:'type', success:true,  startTime:8,  stopTime:9]),
      new TaskResult([id:'5', family:'bar', name:'e', type:'type', success:false, startTime:10, stopTime:15]),
      new TaskResult([id:'6', family:'wee', name:'f', type:'type', success:true,  startTime:16, stopTime:18]),
      new TaskResult([id:'7', family:'wee', name:'g', type:'type', success:true,  startTime:19, stopTime:21]),
      new TaskResult([id:'8', family:'wee', name:'h', type:'type', success:true,  startTime:22, stopTime:24])
    ]
    
    // Prime the simple results map with our faked injected results
    job.getSimpleResults()
    job.metaClass.fetchArtifacts = { -> artifacts }

    return job
  }

  @Test
  void should_product_readme_file_with_best_name() {
    def job = new Job("foo-bar-0", new File("jobDir"), new File("projectDir"), "details")

    assert job.readMeFile != null
    assert job.readMeFile.name == "readme.txt"
  }

  @Test
  void should_produce_readme_file_for_file_that_exists() {
    checkReadmeExits("README.md", [true, true, true])
  }

  @Test
  void should_produce_readme_file_readme_md_exists() {
    checkReadmeExits("readme.md", [false, true, true])
  }

  @Test
  void should_produce_readme_file_README_txt_exists() {
    checkReadmeExits("README.txt", [false, false, true])
  }

  @Test
  void should_produce_readme_file_readme_txt_exists() {
    checkReadmeExits("readme.txt", [false, false, false])
  }

  private checkReadmeExits(expectedFilename, existsValues) {
    def exists = existsValues.reverse() 

    def mockFile = new MockFor(File)
    mockFile.demand.exists() { exists.pop() }
    mockFile.demand.exists() { exists.pop() }
    mockFile.demand.exists() { exists.pop() }
    mockFile.ignore.getName()

    def job = new Job("foo-bar-0", new File("jobDir"), new File("projectDir"), "details")

    mockFile.use {
      assert job.readMeFile.name == expectedFilename
    }
  }

  @Test
  public void should_get_project_name_from_id() {
    def job = mockJob()
    
    job.id = "foo-bar-0"; assert job.project == 'foo-bar'
    job.id = "foo-bar-1"; assert job.project == 'foo-bar'
    job.id = "foo-bar"; assert job.project == 'foo-bar'
  }

  @Test
  public void should_serialize_job_to_simple_map() {
    FileUtils.metaClass.'static'.forceMkdir = { File dir -> }

    def dtSubmitted = new Date() - 3
    def dtStarted = new Date() - 2
    def dtCompleted = new Date() - 1

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)
    job.initialize()
    
    assert job.properties['job.maxCommits'] == "10"
    job.properties['job.maxCommits'] = 2
    job.properties['job.group'] = 'MyGroup'
    job.properties['job.icon'] = '/me.jpg'
    
    // A Job with results must be completed. 
    // Also, a completed job must already have had its stats updated.
    // See run() for proof.
    job.currentStatus = JobStatus.COMPLETED
    job.suites = 3
    job.failed = 2
    job.commits = [[user:'u0',changeset:'c0',link:'l0'],[user:'u1',changeset:'c1',link:'l1'],[user:'u2',changeset:'c2',link:'l2']]
    job.metaClass.fetchTags = { -> ['foo', 'bar'] }
    job.satisfiedTriggers << "trigger1"
    job.satisfiedTriggers << "trigger2"

    AckManager.instance.ackedJobs = ['foo-bar-0': 'fred']

    job.toSimple().with { s ->
      assert s.id            == 'foo-bar-0'
      assert s.project       == 'foo-bar'
      assert s.details       == 'details'
      assert s.status        == 'COMPLETED'
      assert s.satisfiedTriggers == 'trigger1,trigger2'
      assert s.startedDate   == dtStarted
      assert s.completedDate == dtCompleted
      assert s.submittedDate == dtSubmitted
      assert s.duration      > ((24 * 60 * 60 * 1000) - 100) && s.duration < ((25 * 60 * 60 * 1000) + 100)
      assert s.suites        == 3
      assert s.failed        == 2
      assert s.logFile.endsWith("/toxic.log")
      assert s.logSize       == 0
      assert s.commits       == [[user:'u1',changeset:'c1',link:'l1'],[user:'u2',changeset:'c2',link:'l2']]
      assert s.prevFailed    == 0
      assert s.tags          == ['foo', 'bar']
      assert s.group         == 'MyGroup'
      assert s.paused        == false
      assert s.acked         == 'fred'
      assert s.icon          == '/me.jpg'
    }
  }

  @Test
  public void should_include_pause_status_in_to_simple() {
    File tempDir
    try {
      tempDir = File.createTempDir()

      def jobs = []
      def jobManager = new JobManager("url") {
        String getConfigDir() { tempDir.absolutePath }
      }

      jobs << new Job(id: 'paused-ci-0')
      jobs << new Job(id: 'new-ci-0')
      jobs << new Job(id: 'toggled-ci-0')

      PauseManager.instance.pauseProject(jobManager, 'paused-ci')

      assert jobs.find { j -> j.id == 'paused-ci-0' }.toSimple().paused == true
      assert jobs.find { j -> j.id == 'new-ci-0' }.toSimple().paused == false
      assert jobs.find { j -> j.id == 'toggled-ci-0' }.toSimple().paused == false

      PauseManager.instance.pauseProject(jobManager, 'toggled-ci')
      assert jobs.find { j -> j.id == 'toggled-ci-0' }.toSimple().paused == true

      PauseManager.instance.unpauseProject(jobManager, 'toggled-ci')
      assert jobs.find { j -> j.id == 'toggled-ci-0' }.toSimple().paused == false
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  public void should_serialize_last_completed_task() {
    def dtSubmitted = df.parse('2015-05-01 00:00:00.000')
    def dtStarted   = df.parse('2015-05-01 00:00:01.000')
    def dtCompleted = df.parse('2015-05-01 00:00:35.000')

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)
    job.lastCompleted = new TaskResult([id:'9', family:'bee', name:'buzz', type:'type', success:true,  startTime:1, stopTime:100]).toSimple()

    job.toSimple().with { s ->
      assert s.lastCompleted != null
      assert s.lastCompleted.suite == "bee"
      assert s.lastCompleted.name == "buzz"
      assert s.lastCompleted.stopTime == 100
    }
  }

  @Test
  public void should_detect_if_updated_since() {
    def dtSubmitted = df.parse('2015-05-01 00:00:00.000')
    def dtStarted   = df.parse('2015-05-01 00:00:01.000')
    def dtCompleted = df.parse('2015-05-01 00:00:35.000')

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)
    job.updateStatus(JobStatus.COMPLETED)

    assert job.isUpdatedSince(dtCompleted-1)
    assert job.isUpdatedSince(dtCompleted)
    assert !job.isUpdatedSince(dtCompleted+1)

    job.updateStatus(JobStatus.PENDING)

    assert job.isUpdatedSince(dtCompleted-1)
    assert job.isUpdatedSince(dtCompleted)
    assert job.isUpdatedSince(dtCompleted+1)
  }

  @Test
  public void should_serialize_job_to_simple_map_with_bad_date() {
    TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    def dtSubmitted = new Date() - 3
    def dtStarted = null
    def dtCompleted = null

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)

    job.toSimple().with { s ->
      assert s.startedDate   == dtStarted
      assert s.completedDate == dtCompleted
      assert s.submittedDate == dtSubmitted
      assert s.duration      == 0
    }
  }

  @Test
  public void should_serialize_job_to_suite_breakdown() {
    def dtSubmitted = df.parse('2015-05-01 00:00:00.000')
    def dtStarted   = df.parse('2015-05-01 00:00:01.000')
    def dtCompleted = df.parse('2015-05-01 00:00:35.000')

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)
    job.results << new TaskResult([id:'9', family:'bee', name:'i', type:'type', success:true,  startTime:0, stopTime:0])

    assert job.toSuiteBreakdown(1, 20) == [
      [suite: 'foo', family: 'foo', tasks: 3, complete: true, success: false, startTime: 1, duration: 4,],
      [suite: 'bar', family: 'bar', tasks: 2, complete: true, success: false, startTime: 8, duration: 6],
      [suite: 'wee', family: 'wee', tasks: 1, complete: true, success: true, startTime: 16, duration: 2]
    ]

    assert job.toSuiteBreakdown(20, Long.MAX_VALUE) == [
      [suite: 'wee', family: 'wee', tasks: 2, complete: true, success: true, startTime: 19, duration: 4]
    ]
  }

  @Test
  public void should_serialize_job_to_task_breakdown() {
    def dtSubmitted = new Date() - 3
    def dtStarted = new Date() - 2
    def dtCompleted = new Date() - 1

    def job = mockJob(dtSubmitted, dtStarted, dtCompleted)

    assert job.toTaskBreakdown('foo') == [
      [id:'1', family:'foo', suite:'foo', name:'a', type:'type', success:true, error:null, startTime:1, stopTime:2, complete:true, duration:1 ],
      [id:'2', family:'foo', suite:'foo', name:'b', type:'type', success:false, error:null, startTime:3, stopTime:5, complete:true, duration:2 ],
      [id:'3', family:'foo', suite:'foo', name:'c', type:'type', success:false, error:null, startTime:6, stopTime:7, complete:true, duration:1 ]
    ]
  }

  @Test
  void should_initialize_and_end() {

    def createdDir = 0
    GroovyEvaluator.metaClass.'static'.eval = { def script, Map mem -> return "ok" }
    FileUtils.metaClass.'static'.forceMkdir = { File dir -> createdDir++ }
    def job = mockJob(null, null, null)
    job.details = """job.logFile=${tmpFile.canonicalPath}
                     job.init.script.1=foo1
                     job.init.script.2=foo2
                     job.end.script.1=end1
                     job.end.script.2=end2.toString()"""
    job.initialize()
    assert job.artifactsDir.canonicalPath.endsWith("/artifacts")
    assert createdDir == 2
    assert job.properties['job.workDir'].endsWith("/jobDir")
    assert job.properties['project.workDir'].endsWith("/projectDir")
    assert job.properties['job.init.script.1.result'] == "ok"
    assert job.properties['job.init.script.2.result'] == "ok"
    assert !job.properties['job.end.script.1.result']
    assert !job.properties['job.end.script.2.result']
    assert job.properties.job == job
    assert job.properties.junitFile.endsWith("/jobDir/toxic.xml")
    assert job.properties.log.name == "foo-bar-0"

    job.end()
    assert job.properties['job.end.script.1.result'] == "ok"
    assert job.properties['job.end.script.2.result'] == "ok"

    stopNotifications()
    
    assert notifications[EventType.JOB_CHANGED].size() == 1
  }

  @Test
  void should_attach_repository() {

    def scenarios = [
      [
          repoType: 'toxic.job.HgRepository', 
          repoUrl: 'http://foo.repo', 
          repoBranch: 'default',
          repoChangesetUrlTemplate: 'http://go/@@changeset@@'
      ],
      [
          repoType: 'toxic.job.GitRepository', 
          repoUrl: 'http://foo.repo', 
          repoBranch: 'master',
          repoChangesetUrlTemplate: 'http://go/@@changeset@@'
      ]
    ]

    scenarios.each { sc ->
      def job = mockJob(null, null, null)
      job.properties['job.repoType'] = sc.repoType
      job.properties['job.repoUrl'] = sc.repoUrl
      job.properties['job.repoBranch'] = sc.repoBranch
      job.properties['job.repoChangesetUrlTemplate'] = sc.repoChangesetUrlTemplate
      job.projectWorkDir = new File("fake")
      
      def madeDir

      FileUtils.metaClass.'static'.forceMkdir = { File f -> madeDir=true}
      try {
        job.attachRepository()
      } finally {
        job.projectWorkDir.delete()
      }

      assert madeDir
      assert job.repo instanceof SourceRepository
      assert job.repo.branch == sc.repoBranch
      assert job.repo.changesetUrlTemplate == sc.repoChangesetUrlTemplate
    }
  }

  @Test
  void should_update_repository_when_initializing() {
    boolean repoInitialized = false
    boolean repoUpdated = false

    SourceRepositoryFactory.metaClass.'static'.make = { String t, String l, String r, String u, String b -> 
      [update:{ -> repoUpdated = true; return [] }] as SourceRepository
    }

    def job = mockJob(null, null, null)
    job.properties['job.repoType'] = 'toxic.job.HgRepository'
    job.properties['job.repoUrl'] = 'http://foo.repo'

    job.initialize()

    assert job.repo instanceof SourceRepository
    assert repoUpdated
  }

  @Test
  void should_load_job_details_from_repo_when_initializing() {
    def job = mockJob(null, null, null)
    File tempDir
    try {
      tempDir = File.createTempDir()
      File propFile = new File(tempDir, 'toxic.job')
      propFile.createNewFile()

      job.projectWorkDir = tempDir
      job.initialize()
      assert null == job.properties.get('foo')

      propFile.text = 'foo=bar'
      job.initialize()
      assert 'bar' == job.properties.get('foo')
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_repeat() {
    def job = mockJob(null, null, null)
    assert !job.shouldRepeat()
    job.repeat()
    assert job.shouldRepeat()
  }

  @Test
  void should_discard() {
    def job = mockJob(null, null, null)
    assert !job.shouldDiscard()
    job.discard()
    assert job.shouldDiscard()
  }
  
  @Test
  void should_fetch_artifacts() {
    def job = mockJob()
    job.metaClass = null
    def isDir = false
    job.artifactsDir.metaClass.isDirectory = { true }
    job.artifactsDir.metaClass.eachFile = { Closure c -> c(new Object() { def getName() { "f1"}; def size() { 123 }; def isDirectory() { isDir } })}
    def a = job.fetchArtifacts()
    assert a == [[name: 'f1', size: 123]]
    
    isDir = true
    a = job.fetchArtifacts()
    assert !a
  }

  @Test
  void should_fetch_artifact() {
    File.metaClass.isFile = { -> true }
    File.metaClass.newInputStream = { -> "lines"}
    def job = mockJob()
    assert job.fetchArtifact("anything.txt")?.contains("lines")
  }

  @Test
  void should_fetch_tags() {
    def job = new Job()

    job.metaClass.fetchArtifacts = { -> 
      [ 
        [name:'not_a_tag', size:1],
        [name:'foo.tag', size:1], 
        [name:'tag', size:1], 
        [name:'.tag', size:1], 
        [name:'just_a_file.txt', size:1], 
        [name:'bar.tag', size:1] ] 
    }

    assert job.fetchTags() == ['foo', 'bar']
  }

  @Test
  void should_determine_if_job_has_tag() {
    def job = new Job()

    job.metaClass.fetchTags = { -> ['foo', 'bar'] }

    assert job.hasTag('foo')
    assert job.hasTag('bar')
    assert !job.hasTag('no_tag')
  }
  
  @Test
  void should_fetch_log() {
    def contents = """line1
line2
line3
line4"""
    tmpFile.text = contents
    def job = mockJob()
    job.properties = ['job.logFile':tmpFile.canonicalPath]
    def res = job.fetchLog(0, 1024)
    assert res.offset == 0
    assert res.size == 23
    assert res.remaining == 0
    assert res.log == contents

    job.currentStatus = JobStatus.RUNNING
    res = job.fetchLog(1, 1024)
    assert res.offset == 1
    assert res.size == 22
    assert res.remaining == 0
    assert res.log == contents[1..-1]
    assert res.job.status == "RUNNING"

    job.currentStatus = JobStatus.COMPLETED
    res = job.fetchLog(3, 1)
    assert res.offset == 3
    assert res.size == 1
    assert res.remaining == 19
    assert res.log == contents[3..3]
    assert res.job.status == "COMPLETED"
  }

  @Test
  void should_get_log_head() {
    def contents = """line1
line2
line3
line4"""
    tmpFile.text = contents
    def job = mockJob()
    job.properties = ['job.logFile':tmpFile.canonicalPath]
    def res = job.logHead(2)
    assert res == """line1
line2
"""

    res = job.logHead(20)
    assert res == """line1
line2
line3
line4
"""
  }

  @Test
  void should_get_log_tail() {
    def contents = """line1
line2
line3
line4"""
    tmpFile.text = contents
    def job = mockJob()
    job.properties = ['job.logFile':tmpFile.canonicalPath]
    def res = job.logTail(1)
    assert res == """
line4"""
    res = job.logTail(2)
    assert res == """
line3
line4"""

    res = job.logTail(20)
    assert res == """line1
line2
line3
line4"""
  }

  @Test
  void should_halt() {
    def job = mockJob()
    job.halt()
    assert job.results[-1].family == "job aborted"
  }

  @Test
  void should_abort_job_on_git_errors() {
    def job = mockJob()
    job.metaClass.initialize = { -> throw new GitCommandException("oopsie")}

    job.call()

    def lastTaskResult = job.results[job.results.size() - 1]

    assert 3 == job.failed
    assert false == lastTaskResult.success
    assert "oopsie" == lastTaskResult.family
    assert "Aborted" == lastTaskResult.name
  }
  
  @Test
  void should_not_overwrite_status() {
    def job = mockJob()

    job.updateStatus(JobStatus.RUNNING)
    assert job.currentStatus == JobStatus.RUNNING

    job.updateStatus(JobStatus.ABANDONED)
    assert job.currentStatus == JobStatus.ABANDONED

    job.updateStatus(JobStatus.COMPLETED)
    assert job.currentStatus == JobStatus.ABANDONED

    job.updateStatus(JobStatus.PENDING)
    assert job.currentStatus == JobStatus.ABANDONED

    job.updateStatus(JobStatus.INITIALIZING)
    assert job.currentStatus == JobStatus.ABANDONED

    job.updateStatus(JobStatus.ENDING)
    assert job.currentStatus == JobStatus.ABANDONED
  }
  
  @Test
  void should_get_run_number() {
    def job = mockJob()
    job.id = "hello-there-123"
    assert job.project == "hello-there"
    assert job.sequence == 123

    job = mockJob()
    job.id = "hello-there"
    assert job.project == "hello-there"
    assert job.sequence == 0
  }
  
  @Test
  void should_call_notifications() {
    def job = mockJob()
    job.properties["job.notification.0"] = TestNotification.name
    assert !job.properties.calledNotifications
    job.callNotifications()
    assert !job.properties.calledNotifications

    job.previousJob = "something"
    job.callNotifications()
    assert job.properties.calledNotifications
  }

  @Test
  void should_publish_notification_when_job_completes() {
    def job = mockJob()
    job.call()

    stopNotifications()

    // There should be 3 notifications:
    //   (1) update() to collect all the results
    //   (1) end()
    //   (1) finally block
    assert notifications[EventType.JOB_CHANGED].size() == 3
  }
  
  @Test
  void should_pause_if_failed() {
    def job = mockJob()
    job.properties["jobManager"] = new JobManager("url")
    job.properties["job.pauseOnFailure"] = "True"

    assert !PauseManager.instance.isProjectPaused("foo-bar")
    job.call()
    assert PauseManager.instance.isProjectPaused("foo-bar")
  }
  
  @Test
  void should_not_pause_if_success() {
    def job
    Job.metaClass.now = { -> job?.failed = 0; return new Date() }
    job = mockJob()
    job.failed = 0
    job.results = []
    job.properties["jobManager"] = new JobManager("url")
    job.properties["job.pauseOnFailure"] = "True"

    assert !PauseManager.instance.isProjectPaused("foo-bar")
    job.call()
    assert !PauseManager.instance.isProjectPaused("foo-bar")
  }

  @Test
  void should_be_stale() {
    def job = mockJob(new Date() - 5, new Date() - 4, new Date() - 3)
    assert !job.isStale()
    
    job.properties['job.maxAgeInDays'] = "1"
    
    assert !job.isStale()
  
    job.currentStatus = JobStatus.COMPLETED
    assert job.isStale()

    job.properties['job.maxAgeInDays'] = "2"
    assert job.isStale()

    job.properties['job.maxAgeInDays'] = "3"
    assert !job.isStale()

    job.properties['job.maxAgeInDays'] = "4"
    assert !job.isStale()

    job.properties['job.maxAgeInDays'] = "5"
    assert !job.isStale()

    job.properties['num'] = 1
    job.properties['job.maxAgeInDays'] = "`return memory.num + 1`"
    assert job.isStale()

    job.properties['num'] = 4
    job.properties['job.maxAgeInDays'] = "`return memory.num + 1`"
    assert !job.isStale()
    
    job.properties['job.maxAgeInDays'] = "1"
    assert job.isStale()
    
    job.metaClass.fetchArtifacts = { return [new File("something.tag")]}
    assert !job.isStale()

    job.metaClass.fetchArtifacts = { return [new File("something.notag")]}
    assert job.isStale()
  }

  @Test
  void should_auto_trigger_actions() {
    def saved
    def exists = false
    def fileMock = new MockFor(File)
    fileMock.demand.isFile(0..1) { return exists }
    fileMock.demand.setText(0..1) { String str -> saved = str }
    def job = mockJob(new Date() - 5, new Date() - 4, new Date() - 3)
    assert !job.collectValidActions()

    job.properties['job.actionIfComplete.Something'] = "return true"
    job.properties['job.actionIfComplete.Something.autoTriggerAfterSeconds'] = "12"

    // Don't run since job is not yet complete
    job.currentStatus = JobStatus.RUNNING
    fileMock.use {
    job.performAutoTriggerActions()
      assert !saved
    }

    // Job complete so run the auto-trigger
    job.currentStatus = JobStatus.COMPLETED
    fileMock.use {
      job.performAutoTriggerActions()
      assert saved.isNumber()
    }

    // Job complete but do not run the auto-trigger with high number of trigger seconds
    // (test for integer overflow)
    saved = false
    job.properties['job.actionIfComplete.Something.autoTriggerAfterSeconds'] = "2592000"
    job.currentStatus = JobStatus.COMPLETED
    fileMock.use {
      job.performAutoTriggerActions()
      assert !saved
    }
  }
  
  @Test
  void collect_actions() {
    def job = mockJob(new Date() - 5, new Date() - 4, new Date() - 3)
    assert !job.collectValidActions()

    job.properties['job.action.a1'] = "s1"
    job.properties['job.action.a2'] = "s2"
    job.properties['job.actionIfComplete.a3'] = "s3"
    job.properties['job.actionIfComplete.a4'] = "s4"
    job.properties['job.actionIfCompleteSuccess.a5'] = "s5"
    job.properties['job.actionIfCompleteSuccess.a6'] = "s6"
    job.properties['job.actionIfCompleteWithFailure.a7'] = "s7"
    job.properties['job.actionIfCompleteWithFailure.a8'] = "s8"
    job.properties['job.actionIfFailure.a9'] = "s9"
    job.properties['job.actionIfFailure.a9.foo'] = "s9"

    job.failed = 0
    job.currentStatus = JobStatus.RUNNING
    assert job.collectValidActions() == [
      "job.action.a1":[name:"a1"],
      "job.action.a2":[name:"a2"]
      ]

    job.currentStatus = JobStatus.COMPLETED
    assert job.collectValidActions() == [
      "job.action.a1":[name:"a1"],
      "job.action.a2":[name:"a2"],
      "job.actionIfComplete.a3":[name:"a3"],
      "job.actionIfComplete.a4":[name:"a4"],
      "job.actionIfCompleteSuccess.a5":[name:"a5"],
      "job.actionIfCompleteSuccess.a6":[name:"a6"]
      ]

    job.failed++
    assert job.collectValidActions() == [
      "job.action.a1":[name:"a1"],
      "job.action.a2":[name:"a2"],
      "job.actionIfComplete.a3":[name:"a3"],
      "job.actionIfComplete.a4":[name:"a4"],
      "job.actionIfCompleteWithFailure.a7":[name:"a7"],
      "job.actionIfCompleteWithFailure.a8":[name:"a8"],
      "job.actionIfFailure.a9":[name:"a9"]
      ]

    job.currentStatus = JobStatus.RUNNING
    assert job.collectValidActions() == [
      "job.action.a1":[name:"a1"],
      "job.action.a2":[name:"a2"],
      "job.actionIfFailure.a9":[name:"a9"]
      ]
  }
  
  @Test
  void collect_action_auths() {
    def job = mockJob(new Date() - 5, new Date() - 4, new Date() - 3)
    assert !job.collectActionAuths('job.action.a1')

    job.properties['job.action.a1.auths'] = "auth1,auth2"
    assert job.collectActionAuths('job.action.a1') == ['auth1','auth2']

    job.properties['job.action.a1.auths.foo-bar'] = "auth4;auth5,auth6"
    assert job.collectActionAuths('job.action.a1') == ['auth4','auth5','auth6']

    job.properties['secure.job.action.a1.auths'] = "auth0"
    assert job.collectActionAuths('job.action.a1') == ['auth0']

    job.properties['secure.job.action.a1.auths.foo-bar'] = "auth7"
    assert job.collectActionAuths('job.action.a1') == ['auth7']
  }

  @Test
  void should_perform_action() {
    def job = new Job()
    job.properties["x.Y.z"] = "return 'abc'"
    assert job.performAction("x.y.z",'') == 'abc'
  }

  @Test
  void should_not_trigger_multiple_times() {
    def date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.111").toCalendar()
    Calendar.metaClass.'static'.getInstance = { -> return date }
    def job1 = new Job()
    job1.properties = new ToxicProperties()
    job1.properties.doDir = "/"
    job1.lastStartTimeTriggered = null
    job1.properties['job.trigger.startTime'] = '14:23'
    assert job1.requirementsSatisfied()
    job1.currentStatus = JobStatus.COMPLETED

    def job2 = new Job()
    job2.properties = new ToxicProperties()
    job2.properties.doDir = "/"
    job2.lastStartTimeTriggered = null
    job2.properties['job.trigger.startTime'] = '14:23'
    job1.startedDate = date.time
    job2.properties.jobManager = new Object() { def findLatestJob = { project, status -> job1 } }
    assert !job2.requirementsSatisfied()
  }

  @Test
  void should_check_requirements_for_startTime_trigger() {
    def date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.111").toCalendar()
    Calendar.metaClass.'static'.getInstance = { -> return date }
    def job = new Job()
    job.properties = new ToxicProperties()
    job.properties.doDir = "/"
    
    assert job.requirementsSatisfied()

    job.properties['job.trigger.startTime'] = 'a'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '14'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '14:23'
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers.contains("startTime(14:23)")

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '14:20'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '14:23:34'
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers.contains("startTime(14:23:34)")

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '02pm'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '02:20pm'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '02:23:34pm'
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers.contains("startTime(02:23:34pm)")

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '2:23:34pm'
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers.contains("startTime(2:23:34pm)")

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '2:23:35pm'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '2:23:33pm'
    assert job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '2:23:32pm'
    assert job.requirementsSatisfied()
    // Should not rerun the job if it already triggered for that time
    job.properties['job.trigger.startTime'] = '2:23:32pm'
    assert !job.requirementsSatisfied()
    // Should rerun the job if it triggered for a different start time
    job.properties['job.trigger.startTime'] = '2:23:00pm'
    assert job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:00:04.111").toCalendar()
    job.properties['job.trigger.startTime'] = '2pm'
    assert job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '2'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '14'
    assert job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '12,14,16'
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers.contains("startTime(12,14,16)")

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '12,16'
    assert !job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '10pm, 2pm, 6pm'
    assert job.requirementsSatisfied()

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '12'
    assert !job.requirementsSatisfied(true)
    
    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '12'
    assert job.requirementsSatisfied(false)

    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '10pm, 2pm, 6pm'
    assert job.requirementsSatisfied()

    job.properties["job.startTimeTriggerRangeMillis"] = 120000
    job.lastStartTimeTriggered = null
    job.properties['job.trigger.startTime'] = '02:22:00pm'
    assert job.requirementsSatisfied(false)

    job.properties = [:]
    assert !job.requirementsSatisfied(false)
  }

  @Test
  void should_check_requirements_for_concurrent_job_runs() {
    def job = new Job()
    job.properties = new ToxicProperties()
    job.properties.doDir = "/"
    
    job.runningRelatedJobs = 0
    assert !job.concurrencyLimitReached()

    job.runningRelatedJobs = 1
    assert job.concurrencyLimitReached()

    job.properties['job.allowConcurrentRuns'] = true
    job.runningRelatedJobs = 1
    assert !job.concurrencyLimitReached()

    job.properties['job.allowConcurrentRuns'] = "TRUE"
    job.runningRelatedJobs = 1
    assert !job.concurrencyLimitReached()
    job.runningRelatedJobs = 2
    assert !job.concurrencyLimitReached()

    job.properties['job.allowConcurrentRuns'] = "False"
    job.runningRelatedJobs = 1
    assert job.concurrencyLimitReached()
    job.runningRelatedJobs = 0
    assert !job.concurrencyLimitReached()
  }

  @Test
  void should_check_requirements_for_repoCommit_trigger() {
    def job = new Job()
    job.properties = new ToxicProperties()

    job.properties.doDir = "/"
    
    assert job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'false'
    assert !job.requirementsSatisfied()
    assert !job.satisfiedTriggers?.contains("repoCommit")

    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    assert !job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    job.properties['job.repoType'] = 'toxic.job.HgRepository'
    assert !job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    job.properties['job.repoUrl'] = 'http://foo'
    assert !job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    job.properties['job.repoType'] = 'UNKNOWN'
    job.properties['job.repoUrl'] = 'http://foo'
    assert !job.requirementsSatisfied()
  }

  @Test
  void should_not_trigger_repoCommit_if_repo_does_not_have_changes() {
    SourceRepositoryFactory.metaClass.'static'.make = { String t, String l, String r, String u, String b -> 
      [hasChanges:{ -> false }] as SourceRepository
    }

    def job = new Job()
    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    job.properties['job.repoType'] = 'toxic.job.HgRepository'
    job.properties['job.repoUrl'] = 'http://foo'
    job.properties.doDir = "/"

    assert !job.requirementsSatisfied()
  }

  @Test
  void should_trigger_repoCommit_if_repo_has_changes() {
    SourceRepositoryFactory.metaClass.'static'.make = { String t, String l, String r, String u, String b -> 
      [hasChanges:{ -> true }] as SourceRepository
    }

    def job = new Job()
    job.properties = new ToxicProperties()
    job.properties['job.trigger.repoCommit'] = 'true'
    job.properties['job.repoType'] = 'toxic.job.HgRepository'
    job.properties['job.repoUrl'] = 'http://foo'
    job.properties.doDir = "/"
    job.projectWorkDir = new File("fake")

    try {
      assert job.requirementsSatisfied()
      assert job.satisfiedTriggers?.contains("repoCommit(true)")
    } finally {
      job.projectWorkDir.delete()
    }
  }
  
  @Test
  void should_trigger_if_depends_on_other_job() {
    def jobManager = new JobManager("htto://fake")

    def startTime0 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.111")
    def endTime0 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.211")
    def job0 = mockJob(null, startTime0, endTime0)
    job0.currentStatus = JobStatus.COMPLETED
    job0.id = "test.job-0"
    jobManager.jobs << job0

    def startTime1 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.311")
    def endTime1 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.411")
    def job1 = mockJob(null, startTime1, endTime1)
    job1.currentStatus = JobStatus.COMPLETED
    job1.failed = 0
    job1.id = "other.job-0"
    jobManager.jobs << job1
    
    def job = mockJob(null, null, null)
    job.id = "test.job-1"
    job.properties = new ToxicProperties()
    job.properties.jobManager = jobManager
    job.properties.doDir = "/"

    job.properties['job.trigger.dependsOn'] = 'notexist'
    assert !job.requirementsSatisfied()

    job.properties['job.trigger.dependsOn'] = 'other'
    assert job.requirementsSatisfied()

    job1.completedDate -= 1
    assert !job.requirementsSatisfied()
  }
  
  @Test
  void should_trigger_if_event() {
    def jobManager = new JobManager("htto://fake")

    def startTime0 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.111")
    def endTime0 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2015-06-03 14:23:34.211")
    def job0 = mockJob(null, startTime0, endTime0)
    job0.currentStatus = JobStatus.COMPLETED
    job0.id = "test.job-0"
    jobManager.jobs << job0

    def startTime1 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2115-06-03 14:23:34.311")
    def endTime1 = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2115-06-03 14:23:34.411")
    def job1 = mockJob(null, startTime1, endTime1)
    job1.currentStatus = JobStatus.COMPLETED
    job1.failed = 0
    job1.id = "other.job-0"
    jobManager.jobs << job1
    
    def job = mockJob(null, null, null)
    job.id = "test.job-1"
    job.properties = new ToxicProperties()
    job.properties.jobManager = jobManager
    job.properties['job.trigger.event'] = 'test'
    job.properties.doDir = "/"
    assert !job.requirementsSatisfied()

    EventManager.instance.init("/tmp/JobTestEvent")
    EventManager.instance.createEvent("test")
    assert job.requirementsSatisfied()

    job.id = "other.job-1"
    job.cachedProject = null
    assert !job.requirementsSatisfied()
  }

  @Test
  void should_check_requirements_for_immediate_trigger() {
    def job = new Job()

    job.properties = new ToxicProperties()
    job.properties.doDir = "/"
    job.properties['job.trigger.immediate'] = 'false'
    assert !job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.immediate'] = null
    job.properties.doDir = "/"
    assert job.requirementsSatisfied()

    job.properties = new ToxicProperties()
    job.properties['job.trigger.immediate'] = 'true'
    job.properties.doDir = "/"
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers?.contains("immediate(true)")

    job.properties = new ToxicProperties()
    job.properties['job.trigger.immediate'] = 'Y'
    job.properties.doDir = "/"
    assert job.requirementsSatisfied()
    assert job.satisfiedTriggers?.contains("immediate(Y)")
  }


  @Test
  void should_parse_junit_xmls_in_dir() {
    def parserMock = new MockFor(JUnitParser)
    parserMock.demand.parseDir() { File file, List results -> results << new TaskResult([:]); return [suites:23,failures:12] }

    parserMock.use {
      def job = new Job()
      job.parseJUnitXmlFilesInDir(new File("somewhere"))
      assert job.suites == 23
      assert job.failed == 12
      assert job.results.size() == 1
    }
  }

  @Test
  void should_update_stats() {

    def tm1 = new TaskMaster()
    def tm2 = new TaskMaster()
    def job = new Job()
    job.currentStatus = JobStatus.RUNNING
    
    job.update()
    assert job.suites == 0
    assert job.failed == 0

    job.agent = new ToxicAgent(taskMasters: [tm1, tm2])
    job.update()
    assert job.suites == 0
    assert job.failed == 0
    
    tm1.results << new TaskResult(family: 'f1', success: false)
    job.update()
    assert job.suites == 1
    assert job.failed == 1
    
    tm1.results << new TaskResult(family: 'f1', success: true)
    tm2.results << new TaskResult(family: 'f2', success: true)
    job.update()
    assert job.suites == 2
    assert job.failed == 1
    
    assert !job.lastCompleted

    tm1.results << new TaskResult(family: 'f1', success: true, stopTime: 4)
    tm1.results << new TaskResult(family: 'f2', success: true, stopTime: 5)
    tm2.results << new TaskResult(family: 'f3', success: true, stopTime: 6)
    tm2.results << new TaskResult(family: 'f4', success: true, stopTime: null)
    job.update()
    assert job.lastCompleted?.family == "f3"

    stopNotifications()

    assert notifications[EventType.JOB_CHANGED].size() == 5
  }

  @Test
  void should_add_remote_result() {
    Job job = new Job(agent: new ToxicAgent())
    assert [] == job.agent.remoteTaskMaster.results
    job.addRemoteResult(new TaskResult(id:1))
    assert 1 == job.agent.remoteTaskMaster.results.size()
    assert 1 == job.agent.remoteTaskMaster.results[0].id
  }

  @Test
  void should_configure_base_logger() {
    def job = new Job(id:'foo')
    def logger = job.generateLogger()

    assert logger
    assert logger.allAppenders.collect { it.class } == []
  }

  @Test
  void should_configure_base_logger_with_logLevel() {
    def job = new Job(id:'foo')
    job.properties['job.logLevel'] = 'WARN'
    def logger = job.generateLogger()

    assert logger
    assert logger.allAppenders.collect { it.class } == []
    assert logger.level == Level.WARN
  }

  @Test
  void should_configure_logger_with_file_appender() {
    def job = new Job(id:'foo')
    job.properties['job.logLayout'] = 'foo'
    job.properties['job.logFile'] = 'logFile'

    def logger = job.generateLogger()

    assert logger
    assert logger.allAppenders.collect { it.class } == [FileAppender.class]

    new File('logFile').delete()
  }

  @Test
  void should_configure_logger_with_splunk_appender() {
    def job = new Job(id:'foo')
    job.properties['job.splunk.loghost'] = 'localhost'
    job.properties['job.splunk.logport'] = '9999'

    def logger = job.generateLogger()

    assert logger
    assert logger.allAppenders.collect { it.class } == [SplunkRawTCPAppender.class]
  }
  
  @Test
  void should_return_environment() {
    def job = new Job(id:'foo')
    assert !job.environment
    
    job = new Job('foo', null, null, "environment=hello") 
    assert job.environment == "hello"
  }

  @Test
  void should_establish_artifacts_dir() {
    def job = new Job(id:'foo')
    assert !job.artifactsDir
    
    job = new Job('foo', new File("/tmp"), null, "artifacts.dir=hello") 
    assert job.artifactsDir.name == "artifacts"

    job = new Job('foo', null, null, "job.artifactsDir=hello") 
    assert job.artifactsDir.name == "hello"
  }

  @Test
  void should_default_props() {
    def job = new Job('foo', new File("/tmp"), null, "artifacts.dir=hello") 
    assert job.properties['job.maxAgeInDays'] == "7"
  }

  @Test
  void should_determine_if_job_succeeded() {
    assert false == new Job(failed:0, currentStatus: JobStatus.RUNNING).successful
    assert false == new Job(failed:1, currentStatus: JobStatus.COMPLETED).successful
    assert false == new Job(failed:1, currentStatus: JobStatus.RUNNING).successful
    assert true  == new Job(failed:0, currentStatus: JobStatus.COMPLETED).successful
  }

  @Test
  void should_create_results_hierarchy() {
    def job = new Job("jobWithResults", new File("a"), new File("a"), "a")
    def task1 = new TaskResult("task1id", "foo/bar/baz", "task1", "Task1Type")
    sleep(100)
    task1.mark()
    job.results = [
        task1,
        new TaskResult("task2id", "foo/bar/bam", "task2", "Task2Type"),
        new TaskResult("task3id", "turtle/turtle/turtle/turtle", "task3", "Task3Type")
    ]
    def results = job.getTaskResultsHierarchy()
    assert results
    assert results.name == 'root'
    assert results.children?.size == 2
    def getChild = {parent, childName -> parent.children.find{child -> child.name == childName}}

    assert getChild(results, 'nope') == null

    assert getChild(results, 'foo') != null
    assert getChild(getChild(results, 'foo'), "bar") != null
    assert getChild(getChild(getChild(results, 'foo'), "bar"), "baz") != null
    def task1Result = getChild(getChild(getChild(getChild(results, 'foo'), "bar"), "baz"), "task1")
    assert task1Result != null
    assert task1Result == task1.toSimple()

    // it's turtles most of the way down
    assert getChild(results, 'turtle') != null
    assert getChild(getChild(results, 'turtle'), "turtle") != null
    assert getChild(getChild(getChild(results, 'turtle'), "turtle"), "turtle") != null
    assert getChild(getChild(getChild(getChild(results, 'turtle'), "turtle"), "turtle"), "turtle") != null
    assert getChild(getChild(getChild(getChild(getChild(results, 'turtle'), "turtle"), "turtle"), "turtle"), "task3") != null
  }

  @Test
  void should_infer_docker_image_repository_name_from_job_id() {
    assert new Job(id:'foo-ci.job-123').imageRepositoryName == 'foo'
    assert new Job(id:'foo-ci.job-0').imageRepositoryName == 'foo'
    assert new Job(id:'foo-branched-ci.job-192').imageRepositoryName == 'foo-branched'
    assert new Job(id:'not-a-ci-job-123').imageRepositoryName == null
    assert new Job(id:'foo').imageRepositoryName == null
  }

  @Test
  void should_use_ecr_registry_property() {
    def job = new Job(id:'not-a-ci-job')
    assert job.imageRepositoryName == null

    job.properties['job.imageRepository'] = 'foo'
    assert job.imageRepositoryName == 'foo'
  }

  @Test
  void should_get_duration() {
    def job = mockJob()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:00:11.123")
    assert job.duration == 10000
  }
}

public class TestNotification {
  public TestNotification() {
  }
  
  public void execute(Job job) {
    job.properties.calledNotifications = true
  }
}


package toxic.job

import org.junit.*
import org.junit.Ignore
import java.nio.file.*
import java.util.concurrent.*
import org.apache.commons.io.*
import toxic.*
import toxic.notification.*
import groovy.mock.interceptor.*

public class JobManagerTest {

  def moved
  def jobManager
  def filetext
  def deletedDir = 0
  def props
  def tmpDir
  def urlProps = [:]
  def fileExists = true

  def subscriber
  def notifications

  @Before
  public void before() {
    tmpDir = new File("/tmp/jobmanagertest")
    new File(tmpDir, "jobs/pending").mkdirs()
    moved = []
    FileUtils.metaClass.'static'.forceMkdir = { File f -> }
    FileUtils.metaClass.'static'.moveFileToDirectory = { File f, File d, boolean b -> moved << [s: f.toString(), t: d.toString()] }
    FileUtils.metaClass.'static'.copyFileToDirectory = { File f, File d, boolean b -> }
    FileUtils.metaClass.'static'.moveToDirectory = { File f, File d, boolean b -> moved << [s: f.toString(), t: d.toString()] }
    File.metaClass.getParentFile = { null }
    File.metaClass.exists = { fileExists }
    File.metaClass.mkdirs = { }
    File.metaClass.setText = { String text -> filetext = text}
    File.metaClass.getText = { return filetext }
    File.metaClass.deleteDir = { deletedDir++ }
    props = """jobManager.maxConcurrentJobs=4
               jobManager.jobDirectory=${tmpDir.canonicalPath}/jobs
               jobManager.pollInterval=100
            """.toString()
    URL.metaClass.openConnection = { return new Object() {
      def setRequestProperty(String key, String value) { urlProps[key] = value }
      def getInputStream() { return new ByteArrayInputStream(props.bytes) }
    } }

    jobManager = new JobManager("http://fakeUrl")
    jobManager.refreshProperties()

    jobManager.metaClass.getConfigDir = { -> "/dev/null" }

    def events = [EventType.JOBS_LOADED, EventType.JOB_CHANGED]
    notifications = [:]
    events.each { e -> notifications[e] = [] }
    subscriber = [handle: { n -> notifications[n.type] << n }] as Subscriber
    NotificationCenter.instance.subscribe(subscriber, events)

    JobManager.considerOnlySuccessfulJobsForAvgDuration = true
    JobManager.groupCommitsForUsersWithSimilarNames = true
  }

  @After
  public void after() {
    Files.metaClass = null
    FileUtils.metaClass = null
    File.metaClass = null
    Paths.metaClass = null
    URL.metaClass = null
    int delAttempts = 0
    while (tmpDir.exists() && delAttempts++ < 60) {
      tmpDir.deleteDir()
      sleep(1)
    }
    JobManager.metaClass = null
    Job.metaClass = null
    ClassLoader.metaClass = null
  }
  
  @Test
  public void should_shutdown_soon() {
    assert !jobManager.shutdownSoon
    jobManager.running = true
    jobManager.shutdown()
    assert jobManager.shutdownSoon
    assert jobManager.running
  }
  
  @Test
  public void should_shutdown() {
    assert !jobManager.shutdownSoon
    jobManager.running = true
    jobManager.shutdown(true)
    assert !jobManager.shutdownSoon
    assert !jobManager.running
  }
  
  @Test
  public void should_queue_url() {
    def file = jobManager.queueJob("test", "hello")
    assert filetext == "hello"
    assert file.name == "test"
  }
  
  @Test
  public void should_fetch_scheduled_jobs() {
    jobManager.mgrprops["jobManager.autoJobUrl.1"] = "http://somewhere.com/some/fake.job"
    jobManager.secureProps["http://somewhere.com/wrong"]="ignore:this"
    jobManager.secureProps["http://somewhere.com/"]="user:pass"
    
    assert !urlProps
    def scheduled = jobManager.fetchAutomatedJobs()
    
    assert scheduled == ['fake.job':props]
    assert urlProps.Authorization == "Basic dXNlcjpwYXNz"
  }

  @Test
  public void should_fetch_auto_repo_urls_without_default_job_props() {
    jobManager.mgrprops["jobManager.autoRepoUrl.1"] = "ssh://somewhere.com/repos/fake"
    jobManager.defaultJobProps = null
    def scheduled = jobManager.fetchAutomatedJobs()
    assert scheduled == ['fake.job':"job.repoUrl=ssh://somewhere.com/repos/fake"]
  }

  @Test
  public void should_fetch_auto_repo_urls_with_default_job_props() {
    jobManager.mgrprops["jobManager.autoRepoUrl.1"] = "ssh://somewhere.com/repos/fake"
    jobManager.defaultJobProps = "foo=bar"
    def scheduled = jobManager.fetchAutomatedJobs()
    assert scheduled == ['fake.job':"job.repoUrl=ssh://somewhere.com/repos/fake\nfoo=bar"]
  }

  @Test
  public void should_fetch_archived_jobs() {
    def mockFiles = [new File("1.job-0"), new File("2.job-0"), new File("3.job")]

    def count = 0
    File.metaClass.isDirectory { true }
    File.metaClass.isFile { count >= 2 ? false : true }
    File.metaClass.getText { (count++).toString() }
    File.metaClass.eachDir = { Closure c -> 
      mockFiles.each { c(it) } 
    }
    
    def parsed = 0
    def sfmock = new MockFor(SerializedFormatter)
    sfmock.ignore.init() { ToxicProperties props -> }
    sfmock.ignore.parseSummary() { -> parsed++; return [:] }

    def newJobs
    sfmock.use {
      newJobs = jobManager.fetchHistoricJobs(jobManager.completedDir)
    }

    assert newJobs.size() == 3
    assert newJobs[0].id == '1.job-0'
    assert newJobs[0].jobDir == mockFiles[0]
    assert newJobs[0].details == '0'
    assert newJobs[0].results instanceof List
    assert newJobs[1].id == '2.job-0'
    assert newJobs[1].jobDir == mockFiles[1]
    assert newJobs[1].details == '1'
    assert newJobs[1].results instanceof List
    assert newJobs[2].id == '3.job'
    assert newJobs[2].jobDir == mockFiles[2]
    assert newJobs[2].details == 'CORRUPT JOB: ' + mockFiles[2].absolutePath
    assert newJobs[2].results instanceof List
    newJobs.each { assert it.currentStatus == JobStatus.ABANDONED }
    assert parsed == 3
  }
  
  @Test
  public void should_archive() {
    assert !jobManager.archive
    jobManager.archiveJob(new Job("id", new File("test"), new File("project"), "details"))
    assert jobManager.archive.size() == 1
    jobManager.archiveJob(new Job("id2", new File("test2"), new File("project"), "details"))
    assert jobManager.archive.size() == 2
  }
  
  @Test
  public void should_discard() {
    jobManager.archiveJob(new Job("id", new File("test"), new File("project"), "details"))
    jobManager.archiveJob(new Job("id2", new File("test2"), new File("project"), "details"))
    assert jobManager.archive.size() == 2
    jobManager.archive.clone().each { jobManager.discardJob(it) }    
    assert !jobManager.archive
  }

  @Test
  public void should_fetch_new_jobs() {
    def mockFiles = [new File("1.job"), new File("2.job")]

    File.metaClass.eachFileMatch = { pattern, Closure c -> 
      mockFiles.each { c(it) } 
      assert pattern.toString() == ".*.job"
    }

    def newJobs = jobManager.fetchNewJobs()

    assert newJobs.size() == 2
    assert newJobs[0].name == '1.job'
    assert newJobs[1].name == '2.job'
  }

  @Test
  public void should_fetch_processing_jobs() {
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    
    assert !jobManager.fetchProcessingJobs()
    
    jobManager.jobs[2].processing = true
    assert jobManager.fetchProcessingJobs().size() == 1

    jobManager.jobs[1].processing = true
    assert jobManager.fetchProcessingJobs().size() == 2
  }

  @Test
  public void should_fetch_completed_jobs() {
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.jobs << new Job("id1", new File("id1"), new File("project"), "details")
    
    assert !jobManager.fetchCompletedJobs()
    
    jobManager.jobs[1].currentStatus = JobStatus.COMPLETED
    assert jobManager.fetchCompletedJobs().size() == 1

    jobManager.jobs[2].currentStatus = JobStatus.COMPLETED
    jobManager.jobs[2].processing = true
    assert jobManager.fetchCompletedJobs().size() == 1

    jobManager.jobs[2].processing = false
    assert jobManager.fetchCompletedJobs().size() == 2
  }

  @Test
  public void should_fetch_stale_jobs() {

    jobManager.archive << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.archive << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.archive << new Job("id1", new File("id1"), new File("project"), "details")
    jobManager.archive << new Job("id1", new File("id1"), new File("project"), "details") { boolean isStale() { false } }

    jobManager.archive.each { 
      it.metaClass.fetchArtifacts = { -> [] }
      it.completedDate = new Date() - 3
      it.currentStatus = JobStatus.COMPLETED
    }
    
    assert !jobManager.fetchStaleJobs()
    
    jobManager.archive[1].properties['job.maxAgeInDays'] = "1"
    assert !jobManager.fetchStaleJobs()
    
    jobManager.mgrprops['project.minJobRetention'] = "0"
    assert jobManager.fetchStaleJobs().size() == 1

    jobManager.archive[1].properties['job.maxAgeInDays'] = "4"
    assert !jobManager.fetchStaleJobs()

    // Should still only return 1, even if multiple jobs are stale
    jobManager.archive.each { 
      it.properties['job.maxAgeInDays'] = "1"
    }
    jobManager.mgrprops['project.minJobRetention'] = "1"
    assert jobManager.fetchStaleJobs().size() == 1

  }

  @Test
  public void should_not_add_duplicate_jobs() {
    assert jobManager.addJob(new Job(id: 'a'))
    assert jobManager.addJob(new Job(id: 'b'))
    assert !jobManager.addJob(new Job(id: 'a'))
    assert !jobManager.addJob(new Job(id: 'b'))
  }
  
  @Test(expected=Exception)
  public void should_abort_if_invalid_job_file() {
    jobManager.addJob(new File("/"))
  }

  @Test
  public void should_add_multiple_jobs() {
    File.metaClass.isFile = { true }

    jobManager.jobs = [ mockJob("fun", JobStatus.RUNNING ) ]
    assert jobManager.addJob(new File('foo.job'))

    assert jobManager.jobs.size() == 2
    assert jobManager.jobs[1].id == 'foo.job-0'

    assert moved.size() == 1
    assert moved[0].s == 'foo.job'
    assert moved[0].t.endsWith('/jobs/running/foo.job-0')
  }

  @Test
  public void should_not_add_job_to_paused_project() {
    File.metaClass.isFile = { true }

    PauseManager.instance.pauseProject(jobManager,'foopause')
    assert !jobManager.addJob(new File('foopause.job'))
  }

  @Test
  public void should_not_add_job_when_shutting_down() {
    File.metaClass.isFile = { true }

    jobManager.shutdownSoon = true
    assert !jobManager.addJob(new File('some.job'))
  }

  @Test
  public void should_detect_initial_job_run() {
    assert !jobManager.similarJobsExist(new Job())
  }

  @Test
  public void should_detect_modified_job() {
    def oldJobs = [
            new Job("test.job-11", new File("test.job-11"), new File("test"), "its\nall\nin\nthe\ndetails"),
            new Job("test.job-10", new File("test.job-10"), new File("test"), "its\nall\nin\nthe\ndetails"),
            new Job("test.job-12", new File("test.job-12"), new File("test"), "its\nall\nin\nthe\ndetails"),
            new Job("other.job-1", new File("other.job-1"), new File("other"), "foo"),
            new Job("test.job-9",  new File("test.job-9"),  new File("test"), "its\nall\nin\nthe\ndetails")
    ]
    def jm = new JobManager("url")
    jm.metaClass.allJobs = { -> oldJobs }

    // Job details changed
    def newJob = new Job("test.job-13", new File("test.job-13"), new File("test"), "the\ndetails\nhave\nchanged")
    assert jm.jobDefinitionChanged(newJob)

    // Job details did not change
    newJob = new Job("test.job-13", new File("test.job-13"), new File("test"), "its\nall\nin\nthe\ndetails")
    assert !jm.jobDefinitionChanged(newJob)

    // No previous jobs
    jm.metaClass.allJobs = { -> [] }
    newJob = new Job("test.job-13", new File("test.job-13"), new File("test"), "the\ndetails\nhave\nchanged")
    assert jm.jobDefinitionChanged(newJob)
  }
  
  @Test
  public void should_run_job() {
    def fileAdded
    fileExists = false

    def job = mockJob("rerun", JobStatus.COMPLETED)
    def jm = new JobManager("url") {
      public String addJob(File file, boolean reviewTriggers = true) { fileAdded = file; return "rerun-0" }
    }
    assert jm.runJob(job) == "rerun-0"
    assert fileAdded.name == "rerun"
  }

  @Ignore
  @Test
  public void should_unpause_job_when_run_explicitly() {
    def job = mockJob("rerun", JobStatus.COMPLETED)
    def jm = new JobManager("url") {
      public String addJob(File file, boolean reviewTriggers = true) { return "rerun-0" }
    }

    boolean unpaused = false

    PauseManager.instance.metaClass.unpauseProject = { JobManager mgr, String project ->
      unpaused = true
    }

    jm.runJob(job)

    PauseManager.instance.metaClass = null
    
    assert unpaused
  }

  @Test
  public void should_run_pending_job() {
    def fileAdded

    def job = mockJob("rerun-0", JobStatus.COMPLETED)
    def jm = new JobManager("url") {
      public String addJob(File file, boolean reviewTriggers = true) { fileAdded = file; return "rerun-0" }
    }
    assert jm.runJob(job) == "rerun-0"
    assert fileAdded.name == "rerun"
    assert fileAdded.path.contains("pending/")
  }

  private def tempJobFile(jobDetails="") {
    def f = File.createTempFile("temp","temp")
    f.deleteOnExit()
    f.text = jobDetails
    f
  }

  private def mockJobManager(submitted = []) {
    def jm = new JobManager("url")
    jm.metaClass.similarJobsExist = { any -> true }
    jm.metaClass.jobDefinitionChanged = { any -> false }
    jm.jobPool = new ThreadPoolExecutor(1, 1, 60000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue()) {
      public Future submit(Callable task) {
        submitted << task
        return null
      }
    }
    jm
  }

  @Test
  public void should_add_job_if_no_prior_runs() {
    def submitted = []
    def f = tempJobFile()
    try {
      def jm = mockJobManager(submitted)
      jm.metaClass.similarJobsExist = { any -> false }
      def jobId = jm.addJob(f)
      assert jobId && jobId.endsWith("-0")
      assert jm.jobs.size() == 1
      assert submitted.size() == 1
    } finally {
      f.delete()
    }

  }

  @Test
  public void should_not_add_job_if_requirements_not_satisfied() {
    def f = tempJobFile("job.trigger.bogus=woof")
    try {
      def jm = mockJobManager()
      jm.metaClass.similarJobsExist = { any -> true }
      def jobId = jm.addJob(f)
      assert !jobId
    } finally {
      f.delete()
    }
  }

  @Test
  public void should_not_add_job_if_environment_unavailable() {
    def f = tempJobFile()
    try {
      def jm = mockJobManager()
      jm.metaClass.similarJobsExist = { any -> false }
      jm.metaClass.isEnvironmentAvailable = { String any -> false }
      def jobId = jm.addJob(f)
      assert !jobId
    } finally {
      f.delete()
    }
  }

  @Test
  public void should_not_add_job_if_details_changed() {
    def f = tempJobFile()
    def submitted = []
    try {
      def jm = mockJobManager(submitted)
      jm.metaClass.similarJobsExist = { any -> true }
      Job.metaClass.requirementsSatisfied = { boolean any -> false }
      jm.metaClass.jobDefinitionChanged = { Job any -> true }
      assert !jm.addJob(f)
    } finally {
      f.delete()
    }
  }

  @Test
  public void should_complete_job() {
    def job1 = mockJob("foo1.job", JobStatus.COMPLETED)
    def job2 = mockJob("foo2.job", JobStatus.COMPLETED)
    def job3 = mockJob("foo3.job", JobStatus.COMPLETED)
    def job4 = mockJob("foo4.job", JobStatus.COMPLETED)
    jobManager.jobs << job1
    jobManager.jobs << job2
    jobManager.jobs << job3
    jobManager.jobs << job4
    
    jobManager.completeJob(job1)
    assert jobManager.jobs == [job2,job3,job4]
    assert moved.size() == 1
    assert moved[0].s == 'foo1.job'
    assert moved[0].t.endsWith("/jobs/completed")

    jobManager.completeJob(job2)
    assert jobManager.jobs == [job3,job4]
    assert moved.size() == 2
    assert moved[1].s == 'foo2.job'
    assert moved[1].t.endsWith("/jobs/completed")
    assert !filetext

    job3.discard = true
    job3.repeat = true
    job3.details = "jdetails"
    jobManager.completeJob(job3)
    assert jobManager.jobs == [job4]
    assert moved.size() == 2
    assert deletedDir == 1
    assert filetext == "jdetails"
    
    // Handle case where moveToDirectory throws exception (destdir may already exist)
    FileUtils.metaClass.'static'.moveToDirectory = { File f, File d, boolean b -> throw new Exception("some reason") }
    jobManager.completeJob(job4)
    assert jobManager.jobs == []
    assert moved.size() == 2
    assert deletedDir == 2
  }
  
  @Test
  public void should_generate_unique_job_id() {
    File.metaClass.isDirectory = { true }
    File.metaClass.eachDirMatch = { Object regex, Closure cl -> 3.times { cl(new File("test-" + it)) } }
    def actual = jobManager.generateUniqueJobId("test")
    assert "test-3" == actual
  }

  private mockJob(String id, JobStatus status, Date completed = new Date()) {
    def job = new Job(id, new File(id), new File("project"), "details") 
    job.currentStatus = status
    job.completedDate = completed
    job.metaClass.fetchArtifacts = { -> [] }
    return job
  }

  @Test
  public void should_poll_for_new_jobs() {
    File.metaClass.isDirectory = { false }
    File.metaClass.isFile = { true }

    Thread.metaClass.'static'.sleep = { int t -> 
      jobManager.running = false // Break out of loop
      assert t == 5000
    }

    def mockFiles = [new File("1.job"), new File("2.job")]

    File.metaClass.eachFileMatch = { pattern, Closure c -> mockFiles.each { c(it) } }

    boolean pollingHigh, pollingLow
    jobManager.metaClass.runHighPriorityAsyncMaintenanceTasks = { -> pollingHigh = true}
    jobManager.metaClass.runLowPriorityAsyncMaintenanceTasks = { -> pollingLow = true}
    assert !pollingHigh
    assert !pollingLow
    jobManager.run()
    assert pollingHigh
    assert pollingLow
  }

  @Test
  public void should_notify_after_jobs_initially_loaded() {
    File.metaClass.isDirectory = { false }
    File.metaClass.isFile = { true }

    Thread.metaClass.'static'.sleep = { int t -> 
      jobManager.running = false // Break out of loop
    }

    def mockFiles = [new File("1.job"), new File("2.job")]

    File.metaClass.eachFileMatch = { pattern, Closure c -> mockFiles.each { c(it) } }

    jobManager.metaClass.runHighPriorityAsyncMaintenanceTasks = { -> }
    jobManager.metaClass.runLowPriorityAsyncMaintenanceTasks  = { -> }
    jobManager.run()

    assert notifications[EventType.JOBS_LOADED].size() == 1
  }

  @Test
  public void should_respect_running_flag_and_shutdown() {
    boolean slept = true
    jobManager.running = false

    Thread.metaClass.'static'.sleep = { int t -> 
      jobManager.running = false // Break out of loop
      slept = true
    }

    jobManager.run()
    
    assert slept
  }
  
  @Test
  public void should_call_agent() {
    def dir = tmpDir
    def job = new ToxicAgent()
    def details = ""
    def latch = new CountDownLatch(1)
    def results = [new TaskResult("1", "2", "3", "4")]
    def ta = new MockFor(ToxicAgent)
    ta.demand.init { ToxicProperties props ->
      assert props.agentTaskMasterCount == "1"
    }
    ta.ignore.getTaskMasters { -> [] }
    ta.demand.call {
      assert job.currentStatus == JobStatus.RUNNING
      latch.await()
      return results 
    }
    ta.use {
      job = new Job("test", dir, new File("project"), details)
      assert job.currentStatus == JobStatus.PENDING
      def thread = Thread.start() { job.call() }
      latch.countDown()
      thread.join(60000)
      assert job.currentStatus == JobStatus.COMPLETED
      assert job.results == results
    }
  }

  @Test
  public void should_abort_agent() {
    def dir = tmpDir
    def job = new ToxicAgent()
    def details = ""
    def latch = new CountDownLatch(1)
    def throwable = new Exception()
    def results = [new TaskResult("1", "2", "3", "4")]
    def ta = new MockFor(ToxicAgent)
    ta.ignore.init { ToxicProperties props -> }
    ta.ignore.getTaskMasters { -> [] }
    ta.ignore.call { 
      assert job.currentStatus == JobStatus.RUNNING
      latch.await()
      throw throwable
    }
    ta.use {
      job = new Job("test", dir, new File("project"), details)
      assert job.currentStatus == JobStatus.PENDING
      def thread = Thread.start() { job.call() }
      latch.countDown()
      thread.join(60000)
      assert job.currentStatus == JobStatus.COMPLETED

      assert 1 == job.results.size()
      def lastTaskResult = job.results[job.results.size() - 1]
      assert false == lastTaskResult.success
      assert "job aborted" == lastTaskResult.family
      assert "Aborted" == lastTaskResult.name

      assert job.throwable == throwable
    }
  }
  
  @Test
  public void should_determine_job_status() {
    def job = new Job("id", new File("jobDir"), new File("project"), "details")
    assert job.isPending()
    assert !job.isRunning()
    assert !job.isCompleted()
    job.currentStatus = JobStatus.RUNNING
    assert !job.isPending()
    assert job.isRunning()
    assert !job.isCompleted()
    job.currentStatus = JobStatus.INITIALIZING
    assert !job.isPending()
    assert job.isRunning()
    assert !job.isCompleted()
    job.currentStatus = JobStatus.ENDING
    assert !job.isPending()
    assert job.isRunning()
    assert !job.isCompleted()
    job.currentStatus = JobStatus.COMPLETED
    assert !job.isPending()
    assert !job.isRunning()
    assert job.isCompleted()
    job.currentStatus = JobStatus.ABANDONED
    assert !job.isPending()
    assert !job.isRunning()
    assert job.isCompleted()
  }
  
  @Test
  void should_load_secure_props() {
    filetext = """test=this
                  and=that=2
                  =but-not-this
                  """
    jobManager.loadSecureProperties("fake")
    assert jobManager.secureProps.test == "this"
    assert jobManager.secureProps.and == "that=2"
  }

  @Test
  void should_merge_property_sources_for_job() {
    // Values for 'mgrprops'
    props = """unsecuredProp=blah
               someProp=someVal
            """.toString()
    jobManager = new JobManager("http://fakeUrl")
    jobManager.refreshProperties()

    // Values for secureProps
    filetext = """test=this
                  foo=bar
                  """
    jobManager.loadSecureProperties("fake")

    def jobProps = jobManager.jobProperties()
    assert jobProps['test'] == [:]
    assert jobProps['secure.test'] == 'this'
    assert jobProps['foo'] == [:]
    assert jobProps['secure.foo'] == 'bar'

    assert jobProps['someProp'] == 'someVal'
    assert jobProps['secure.someProp'] == [:]
    assert jobProps.unsecuredProp == 'blah'
    assert jobProps.notARealProp == [:]
    assert jobProps.eventManager == EventManager.instance
  }

  @Test
  void should_not_set_default_job_props_when_file_not_found() {
    String name
    ClassLoader.metaClass.'static'.getSystemResourceAsStream = { String actualName ->
      name = actualName
      return null
    }
    jobManager = new JobManager("http://fakeUrl")
    assert null == jobManager.defaultJobProps
    assert 'toxic.job' == name
  }

  @Test
  void should_default_job_properties_when_found_on_classpath() {
    String name
    ClassLoader.metaClass.'static'.getSystemResourceAsStream = { String actualName ->
      name = actualName
      return new ByteArrayInputStream("foo=bar".getBytes())
    }
    jobManager = new JobManager("http://fakeUrl")
    assert "foo=bar" == jobManager.defaultJobProps
    assert 'toxic.job' == name
  }
  
  @Test
  void should_configure_pool() {
    props = """jobManager.maxConcurrentJobs=6""".toString()
    jobManager = new JobManager("http://fakeUrl")
    jobManager.refreshProperties()
    assert jobManager.jobPool.corePoolSize == 6
    assert jobManager.jobPool.maximumPoolSize == 6
  }
  
  @Test
  void should_find_latest_job() {
    def jobManager = new JobManager("http://fakeUrl")
    jobManager.jobs << mockJob("foo3.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-2", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-3", JobStatus.RUNNING)
    jobManager.jobs << mockJob("foo4.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo5.job-1", JobStatus.RUNNING)
    
    // For a specific JobStatus
    assert jobManager.findLatestJob("foo3", JobStatus.COMPLETED)?.id == "foo3.job-2"
    assert jobManager.findLatestJob("foo4", JobStatus.COMPLETED)?.id == "foo4.job-1"
    assert !jobManager.findLatestJob("foo5", JobStatus.COMPLETED)
    assert !jobManager.findLatestJob("foo5", JobStatus.COMPLETED)

    // No particular JobStatus
    assert jobManager.findLatestJob("foo3")?.id == "foo3.job-3"
    assert jobManager.findLatestJob("foo4")?.id == "foo4.job-1"
    assert jobManager.findLatestJob("foo5")?.id == "foo5.job-1"
  }

  @Test
  void should_find_job_by_project() {
    def job
    def jobs
    def projects = [
        ['project': 'aProj', 'id': 'aProj.job-444'],
        ['project': 'testProj', 'id': 'testProj.job-123']
    ]

    def jobManager = new JobManager("http://fakeUrl") {
      def findJob(id) {
        if(jobs && jobs[id]) {
          return jobs[id]
        }
        return job
      }
      def browseJobs() { return jobs.values() }
      def browseProjects() { return projects }
    }
    
    def testJob = new Job()
    jobs = ['testProj.job-123': testJob]

    assert jobManager.findJobByProject('testProj') == testJob
    assert !jobManager.findJobByProject('notThere')
  }

  @Test
  void should_fuzzy_match_a_job_name() {
    def jobManager = new JobManager("http://fakeUrl")
    jobManager.jobs << mockJob("foo3.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-2", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-3", JobStatus.RUNNING)
    jobManager.jobs << mockJob("foo4.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo5.job-1", JobStatus.RUNNING)

    assertFuzzyFind(jobManager, "foo3.job-1", "foo3.job-1")
    assertFuzzyFind(jobManager, "foo3.job-3", "foo3")
    assertFuzzyFind(jobManager, "foo3.job-3", "oo3")
  }

  @Test
  void should_not_find_anything_if_there_are_no_jobs() {
    def jobManager = new JobManager("http://fakeUrl")

    assert [] == jobManager.findJobByFuzzyId("some.job-1")
    assert [] == jobManager.findJobByFuzzyId("")
    assert [] == jobManager.findJobByFuzzyId(null)
  }

  @Test
  void should_not_find_anything_if_the_input_is_not_disabiguating_enough() {
    def jobManager = new JobManager("http://fakeUrl")
    jobManager.jobs << mockJob("foo4.job-1", JobStatus.COMPLETED)

    assert [] == jobManager.findJobByFuzzyId("oo")
    assert [] == jobManager.findJobByFuzzyId("")
    assert [] == jobManager.findJobByFuzzyId(null)
  }

  private assertFuzzyFind(jobManager, expectedJobId, match) {
    def foundJobs = jobManager.findJobByFuzzyId(match)

    assert foundJobs.id == expectedJobId
  }

  @Test
  void should_resolve_included_details() {
    File.metaClass = null
    File file = new File(tmpDir, "include_details.job")
    
    def jobManager = new JobManager(null) {
      def fetchUrlText(url) {
        switch (url) {
          case 'url1': return "url1key=data1\nfoo=bar"
          case 'url2': return "url2key=data2"
          case 'url3': return "url3key=data3"
        }
        return "unexpected"
      }
    }
    def details = """something=here
job.include.2=url2
job.include.1=url1
job.include.3=url3
#job.include.4=url4
another=there
"""
    file.text = details
    def actual = jobManager.resolveFileDetails(file)
    def expected = """url1key=data1
foo=bar
url2key=data2
url3key=data3
something=here
#job.include.2=url2
#job.include.1=url1
#job.include.3=url3
#job.include.4=url4
another=there
"""
    expected = expected.trim()
    actual = actual?.trim()
    assert expected == actual
    def contents = file.text?.trim()
    assert expected == contents
  }
  
  @Test
  void should_cache_url() {
    int count = 0
    def jobManager = new JobManager(null) {
      def fetchUrlInputStream(url) { 
        [text:(count++ > 0 ? "new" : "old")]
      }
    }
    
    assert jobManager.fetchUrlText("url1") == "old"
    assert jobManager.fetchUrlText("url1") == "old"
    assert jobManager.fetchUrlText("url1") == "old"
    assert jobManager.fetchUrlText("url1") == "old"
    jobManager.urlCacheExpireMs = 0
    assert jobManager.fetchUrlText("url1") == "new"
  }
  
  @Test
  void should_detect_environment_in_use() {
    assert jobManager.isEnvironmentAvailable("test")
    
    jobManager.jobs = [ mockJob("fun", JobStatus.RUNNING ) ]
    assert jobManager.isEnvironmentAvailable("test")    

    jobManager.jobs[0].properties.environment = "test"
    assert !jobManager.isEnvironmentAvailable("test")    

    jobManager.jobs[0].currentStatus = JobStatus.COMPLETED
    assert jobManager.isEnvironmentAvailable("test")    
  }

  @Test
  void should_return_raw_job_objects() {
    def jobManager = new JobManager("http://fakeUrl")
    jobManager.jobs << mockJob("foo3.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-2", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo3.job-3", JobStatus.RUNNING)
    jobManager.jobs << mockJob("foo4.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo5.job-1", JobStatus.RUNNING)
 
    def jobs = jobManager.browseJobsUnsimplified("foo3")
    assert jobs.size() == 3
    assert jobs.first() instanceof Job

    jobs = jobManager.browseJobs("foo3")
    assert jobs.size() == 3
    assert jobs.first() instanceof Map
  }

  @Test
  void should_return_projects_since() {
    def dt = new Date()
    def jobManager = new JobManager("http://fakeUrl")
    jobManager.jobs << mockJob("foo1.job-1", JobStatus.COMPLETED, dt-1)
    jobManager.jobs << mockJob("foo2.job-2", JobStatus.RUNNING, dt)
    jobManager.jobs << mockJob("foo3.job-3", JobStatus.RUNNING, dt+1)
    jobManager.jobs << mockJob("foo4.job-1", JobStatus.COMPLETED, dt+2)
    jobManager.jobs << mockJob("foo5.job-1", JobStatus.RUNNING, dt+2)
 
    def jobs = jobManager.browseProjects(dt-2)
    assert jobs.size() == 5
    jobs = jobManager.browseProjects(dt-1)
    assert jobs.size() == 5
    jobs = jobManager.browseProjects(dt+1)
    assert jobs.size() == 4
    jobs = jobManager.browseProjects(dt+2)
    assert jobs.size() == 4
    jobs = jobManager.browseProjects(dt+3)
    assert jobs.size() == 3
    jobs = jobManager.browseProjects(dt+4)
    assert jobs.size() == 3
  }

  @Test
  void should_return_ack_user_when_browsing_projects() {
    AckManager.instance.ackedJobs = ['foo1.job-1': 'fred']

    def jobManager = new JobManager("http://fakeUrl")

    jobManager.jobs << mockJob("foo1.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo2.job-2", JobStatus.RUNNING)
 
    def jobs = jobManager.browseProjects()
    
    assert jobs.collect { j -> j.id + ':' + j.acked } == ['foo1.job-1:fred', 'foo2.job-2:null']

    AckManager.instance.reset()
  }

  @Test
  void should_fetch_latest_jobs() {
    def jobManager = new JobManager("http://fakeUrl")

    jobManager.jobs << mockJob("foo1.job-1", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo1.job-2", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo1.job-3", JobStatus.COMPLETED)
    jobManager.jobs << mockJob("foo2.job-1", JobStatus.RUNNING)
    jobManager.jobs << mockJob("foo2.job-0", JobStatus.COMPLETED)
 
    def jobs = jobManager.fetchLatestJobs()
    assert jobs.size() == 2
    assert jobs[0].id == "foo1.job-3"
    assert jobs[1].id == "foo2.job-1"
  }

  @Test
  void should_generate_metrics() {
    JobManager.considerOnlySuccessfulJobsForAvgDuration = false
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:04:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 123, user:'jack']]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:02:01.123")
    job.cachedProject = "bar"
    job.commits = [[changeset: 123, user:'jack']]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:08:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 456, user:'jack']]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-28 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-28 12:06:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 456, user:null]]
    jobManager.jobs << job
  
    def metrics = jobManager.generateMetrics(job.startedDate - 7)
    assert metrics.dailyMetrics.size() == 3
    assert metrics.dailyMetrics[0] == [date:'2017-07-26',failedJobs:0,totalJobs:1,avgDuration:2,totalDuration:120000]
    assert metrics.dailyMetrics[1] == [date:'2017-07-27',failedJobs:2,totalJobs:2,avgDuration:6,totalDuration:720000]
    assert metrics.dailyMetrics[2] == [date:'2017-07-28',failedJobs:1,totalJobs:1,avgDuration:6,totalDuration:360000]
    assert metrics.projectMetrics == [foo:[failedJobs:3, totalJobs:3, totalDuration:1080000, successRatio:0, failRatio:1, avgDuration:6], bar:[failedJobs:0, totalJobs:1, totalDuration:120000, successRatio:1, failRatio:0, avgDuration:2]]
    assert metrics.commitMetrics == [jack:[failedJobs:2, totalCommits:2, successRatio:0, failRatio:1], unknown:[failedJobs:0, totalCommits:0, successRatio:0, failRatio:0]]
  }

  @Test
  void metrics_should_only_include_the_successful_builds_in_the_avg_duration() {
    JobManager.considerOnlySuccessfulJobsForAvgDuration = true
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:02:01.123")
    job.cachedProject = "bar"
    job.commits = [[changeset: 123, user:'jack']]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-26 12:06:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 789, user:null]]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:04:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 123, user:'jack']]
    jobManager.jobs << job

    job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:08:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [[changeset: 456, user:'jack']]
    jobManager.jobs << job
  
    def metrics = jobManager.generateMetrics(job.startedDate - 7)
    assert metrics.dailyMetrics.size() == 2
    assert metrics.dailyMetrics[0] == [date:'2017-07-26',failedJobs:1,totalJobs:2,avgDuration:2,totalDuration:480000]
    assert metrics.dailyMetrics[1] == [date:'2017-07-27',failedJobs:2,totalJobs:2,avgDuration:0,totalDuration:720000]

    assert metrics.projectMetrics == [
      foo:[failedJobs:3, totalJobs:3, totalDuration:1080000, successRatio:0, failRatio:1, avgDuration:0],
      bar:[failedJobs:0, totalJobs:1, totalDuration:120000, successRatio:1, failRatio:0, avgDuration:2]
    ]
    assert metrics.commitMetrics == [
      jack:[failedJobs:2, totalCommits:2, successRatio:0, failRatio:1],
      unknown:[failedJobs:1, totalCommits:1, successRatio:0, failRatio:1]
    ]
  }

  @Test
  @Ignore("This test is correct. Fix the bug and then unignore the test.")
  public void should_not_count_one_failed_job_per_commit() {
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:04:01.123")
    job.failed = 1
    job.cachedProject = "foo"
    job.commits = [
      [changeset: 100, user:'frank'],
      [changeset: 101, user:'frank'],
      [changeset: 102, user:'frank'],
      [changeset: 103, user:'frank']
    ]
    jobManager.jobs << job
  
    def metrics = jobManager.generateMetrics(job.startedDate - 7)
    assert metrics.commitMetrics == [
      frank:[failedJobs:1, totalCommits:4, successRatio:0, failRatio:1]
    ]
  }

  @Test
  public void should_group_commits_by_the_same_user() {
    JobManager.groupCommitsForUsersWithSimilarNames = true
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:04:01.123")
    job.cachedProject = "foo"
    job.commits = [
      [changeset: 100, user:'Camilo Sanchez'],
      [changeset: 101, user:'camilo.sanchez'],
      [changeset: 102, user:'camilo sanchez'],
      [changeset: 103, user:'cAmilo sAnchez']
    ]
    jobManager.jobs << job
  
    def metrics = jobManager.generateMetrics(job.startedDate - 7)
    assert metrics.commitMetrics == [
      'camilo.sanchez':[failedJobs:0, totalCommits:4, successRatio:1, failRatio:0]
    ]
  }

  @Test
  public void should_not_group_commits_when_the_toggle_is_off() {
    JobManager.groupCommitsForUsersWithSimilarNames = false
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.startedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:00:01.123")
    job.completedDate = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", "2017-07-27 12:04:01.123")
    job.cachedProject = "foo"
    job.commits = [
      [changeset: 101, user:'frank'],
      [changeset: 102, user:'Frank']
    ]
    jobManager.jobs << job
  
    def metrics = jobManager.generateMetrics(job.startedDate - 7)
    assert metrics.commitMetrics == [
      frank:[failedJobs:0, totalCommits:1, successRatio:1, failRatio:0],
      Frank:[failedJobs:0, totalCommits:1, successRatio:1, failRatio:0]
    ]
  }

  @Test
  public void should_get_flattened_metrics() {
    JobManager.considerOnlySuccessfulJobsForAvgDuration = true
    def jobManager = new JobManager("http://fakeUrl")

    def job = new Job()
    job.currentStatus = JobStatus.PENDING
    jobManager.jobs << job

    job = new Job()
    job.currentStatus = JobStatus.RUNNING
    jobManager.jobs << job

    job = new Job()
    job.currentStatus = JobStatus.COMPLETED
    job.failed = 1
    jobManager.jobs << job

    job = new Job()
    job.currentStatus = JobStatus.COMPLETED
    job.failed = 0
    jobManager.jobs << job

    def metrics = jobManager.getMetrics()

    assert metrics['toxic_jobs_total'] == 4
    assert metrics['toxic_jobs_pending'] == 1
    assert metrics['toxic_jobs_running'] == 1
    assert metrics['toxic_jobs_completed'] == 2
    assert metrics['toxic_jobs_succeeded'] == 1
    assert metrics['toxic_jobs_failed'] == 1
  }

  @Test
  public void should_shutdown_and_cancel() {
    def jm = new JobManager("test")
    jm.running = true
    jm.shutdown()
    assert jm.cancelShutdown()
  }

  @Test
  public void should_shutdown_and_fail_to_cancel() {
    def jm = new JobManager("test")
    jm.running = true
    jm.shutdown(true)
    assert !jm.cancelShutdown()
  }

  @Test
  public void should_return_config_repo_dir() {
    def jm = new JobManager("test")
    jm.jobDirectory = "/test"
    assert jm.configRepoDir == "/test/config/repo"
  }

  @Test 
  public void should_prepare_local_url() {
    def jm = new JobManager("test")
    jm.jobDirectory = "/test"
    assert "file:///etc/hosts" == jm.prepareLocalUrl("file:///etc/hosts")
    assert "http://somewhere.com/hosts" == jm.prepareLocalUrl("http://somewhere.com/hosts")
    assert "file:///test/config/repo/something" == jm.prepareLocalUrl("something")
  }

  @Test
  public void should_refresh_config_repo() {
    def jm = new JobManager("test")
    assert !jm.configRepo

    jm.refreshConfigRepo()
    assert !jm.configRepo

    GitRepository.metaClass.exec = {String cmd, boolean failQuietly -> [:] }
    GitRepository.metaClass.exec = {List cmd, boolean failQuietly -> [:] }

    jm.mgrprops.configRepoType = "toxic.job.GitRepository"
    jm.mgrprops.configRepoUrl = "ssh://somewhere"
    try {
      jm.refreshConfigRepo()
    } finally {
      if (jm.configRepoDir) {
        def f = new File(jm.configRepoDir)
        if (f.isDirectory()) {
          f.deleteDir()
        }
      }
    }
    assert jm.configRepo instanceof GitRepository

    GitRepository.metaClass = null
  }
}

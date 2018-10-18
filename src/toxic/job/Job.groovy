package toxic.job

import toxic.*
import toxic.junit.*
import toxic.util.Tail
import toxic.notification.*
import org.apache.commons.io.output.StringBuilderWriter

import java.util.concurrent.*
import java.nio.file.*
import java.text.*
import org.apache.log4j.*
import org.apache.commons.io.*
import toxic.groovy.*
import groovy.time.TimeCategory
import com.splunk.logging.log4j.appender.*

public class Job implements Callable, Comparable, Publisher {
  private static Logger log = Logger.getLogger(Job.class)

  String id
  String name
  File jobDir
  String details
  JobStatus currentStatus
  File projectWorkDir
  File artifactsDir
  SourceRepository repo
  DockerImageRepository imageRepo
  ToxicProperties properties
  ToxicAgent agent
  List<TaskResult> results = []
  Throwable throwable
  Date submittedDate
  Date startedDate
  Date completedDate
  boolean discard
  boolean repeat
  int runningRelatedJobs = 0
  int suites
  int failed  
  String cachedSequence
  String cachedProject
  String cachedImageRepository
  def commits
  transient boolean processing
  long resultsLastAccessedTime
  List<String> satisfiedTriggers = []
  
  // Used to optimize resources for tasks in-progress only
  def taskMasterResultIndex = [:]
  def localSimpleResults = [:]
  def lastCompleted
  def previousJob
  def lastStartTimeTriggered
  
  public Job() {
    properties = new ToxicProperties()
  }

  public Job(String id, File jobDir, File projectDir, String details, ToxicProperties defaultProperties = null) {
    this(id, jobDir, projectDir, details, defaultProperties, id.contains("-") ? id.substring(0, id.lastIndexOf("-")) : id)
  }

  public Job(String id, File jobDir, File projectDir, String details, ToxicProperties defaultProperties, String jobName) {
    log.trace("Starting to construct job; id=${id}")
    def begin = System.currentTimeMillis()
    this.id = id
    this.name = jobName
    this.jobDir = jobDir
    this.projectWorkDir = projectDir
    this.details = details
    this.agent = new ToxicAgent()
    this.submittedDate = now()
    this.properties = defaultProperties ?: new ToxicProperties()
    this.properties.putAll(Main.loadArgs(loadJobDetails()).overrides)
    this.properties.job = this
    if (this.properties["job.artifactsDir"]) {
      this.artifactsDir = new File(this.properties["job.artifactsDir"])
    } else {
      artifactsDir = jobDir
    }
    updateStatus(JobStatus.PENDING)
    log.trace("Finished constructing job; id=${id}; elapsedMs=${System.currentTimeMillis()-begin}")    
  }

  public call() {
    try {
      processing = true

      try {
        log.info("Job initializing; jobId=${id}; jobDir=${jobDir.canonicalPath}")
        updateStatus(JobStatus.INITIALIZING)
        this.startedDate = now()
        initialize()

        if (!shouldDiscard()) {
          log.info("Job running; jobId=${id}; jobDir=${jobDir.canonicalPath}")
          updateStatus(JobStatus.RUNNING)
          this.agent.init(properties)
          results = agent.call() + results
          update()
        } else {
          log.info("Skipping job run due to discard flag; jobId=${id}")
        }        

        log.info("Job ending; jobId=${id}; jobDir=${jobDir.canonicalPath}")
        updateStatus(JobStatus.ENDING)
        end()

        log.info("Job finished; job=${id}; resultCount=${results.size()}")
      } catch (Throwable t) {
        log.warn("Job aborted; job=${id}; throwable=${t}", t)
        throwable = t

        def abortedResult = new TaskResult(id, t.getMessage() ?: "job aborted", "Aborted", this.class.name)
        abortedResult.success = false
        results << abortedResult

        updateSimpleResult(this.localSimpleResults, abortedResult)

        updateStatus(JobStatus.RUNNING)
        update()
      }

      updateStatus(JobStatus.COMPLETED)
      this.completedDate = now()
      if ("true".equalsIgnoreCase(properties["job.pauseOnFailure"].toString()) &&
          failed > 0) {
        PauseManager.instance.pauseProject(properties.jobManager, project)
      }

      saveResults()

      callNotifications()

      use (NotificationCenter) {
        notify(EventType.JOB_CHANGED, this.toSimple())
      }
      
    } finally {
      processing = false
    }
  }

  void addRemoteResult(TaskResult taskResult) {
    this.agent.addRemoteResult(taskResult)
  }
  
  def parseTime(String value, Calendar now) {
    def formats = ["hh:mm:ssaa", "HH:mm:ss", "hh:mmaa", "HH:mm", "hhaa", "HH"]
    for (def format: formats) {
      try {
        def date = Date.parse(format, value).toCalendar()
        date.set(Calendar.MONTH, now.get(Calendar.MONTH))
        date.set(Calendar.DATE, now.get(Calendar.DATE))
        date.set(Calendar.YEAR, now.get(Calendar.YEAR))
        return date
      } catch (Exception e) {
        // Ignore, try the next supported format
      }
    }
    log.error("Unsupported date format; jobId=${id}; value=${value}")
    return null
  }

  boolean immediateTrigger(String value) {
    return value?.toBoolean()
  }
  
  boolean startTimeTrigger(String value) {
    def now = Calendar.instance
    def startTimes = value.split(",").collect { parseTime(it.trim(), now) }
    startTimes = startTimes.findAll { it }
    int millis = 60000
    if (this.properties["job.startTimeTriggerRangeMillis"]) {
      millis = this.properties["job.startTimeTriggerRangeMillis"].toInteger()
    }
    for (def min : startTimes) {
      def max = min.clone()
      max.add(Calendar.MILLISECOND, millis)
      def inRange = (now >= min && now <= max)
      def previousJob
      if (properties?.jobManager) {
        previousJob = properties.jobManager.findLatestJob(project, JobStatus.COMPLETED)
      }
      log.debug("Checked start time trigger; jobId=${id}; lastStartTimeTriggered=${lastStartTimeTriggered}; now=${now.format('yyyy-MM-dd HH:mm:ss.SSS')}; min=${min.format('yyyy-MM-dd HH:mm:ss.SSS')}; max=${max.format('yyyy-MM-dd HH:mm:ss.SSS')}; inRange=${inRange}; previousJobStartDate=${previousJob?.startedDate}")
      if (inRange && (!previousJob?.startedDate || previousJob?.startedDate < min.time)) {
        if (lastStartTimeTriggered != min) {
          lastStartTimeTriggered = min
          return true
        }
      } else {
        lastStartTimeTriggered = null
      }
    }
    return false
  }

  boolean repoCommitTrigger(String value) {
    attachRepository()

    if (!repo || !"true".equalsIgnoreCase(value)) 
      return false
   
    return SourceRepoMonitor.instance.hasRepoChanged(repo)
  }
  
  boolean dependsOnTrigger(String value) {
    def previousJob = properties?.jobManager?.findLatestJob(project, JobStatus.COMPLETED)
    def latestOtherJob = properties?.jobManager?.findLatestJob(value, JobStatus.COMPLETED)
    def result = (latestOtherJob && !latestOtherJob?.failed && (latestOtherJob.completedDate > previousJob.startedDate)) 
    log.debug("dependsOn Results; latestOtherJobFailed=${latestOtherJob?.failed}; latestCompletedDate=${latestOtherJob?.completedDate?.format('yyyy-MM-dd HH:mm:ss.SSS')}; previousStartedDate=${previousJob?.startedDate?.format('yyyy-MM-dd HH:mm:ss.SSS')}")
    return result
  }

  boolean eventTrigger(String value) {
    def previousJob = properties?.jobManager?.findLatestJob(project, JobStatus.COMPLETED)
    def event = EventManager.instance.findEvent(value)
    return (event && (event.time > previousJob?.startedDate?.time)) 
  }

  def concurrencyLimitReached() {
    return runningRelatedJobs > 0 && !this.properties.isTrue("job.allowConcurrentRuns")
  }
  
  def requirementsSatisfied(boolean reviewTriggers = true) {
    if (!this.properties || !this.properties.keySet().find { it.startsWith("doDir") }) {
      log.error("Job does not have a valid property set, aborting job; props=\"${this.properties}\"")
      return false
    }
    log.trace("Starting detection of job requirements satisfied; jobId=${id}")
    def begin = System.currentTimeMillis()
    def triggered = false
    def triggerExists = false
    satisfiedTriggers = []
    if (reviewTriggers) {
      this.properties?.forProperties("job.trigger.") { key, value ->
        if (value) {
          triggerExists = true
          try {
            def trigger = key.substring(key.lastIndexOf(".") + 1)
            def thisTriggered = "${trigger}Trigger"(value)
            if (thisTriggered) {
              log.info("Trigger satisfied; id=${id}; trigger=${trigger}; value=${value}")
              satisfiedTriggers << "${trigger}(${value})".toString()
            }
            triggered |= thisTriggered
          } catch (Exception e) {
            log.error("Unable to validate trigger; trigger=${key}; value=${value}; reason=${JobManager.findReason(e)}", e)
          }
        }
      }
    }
    log.trace("Finished detecting if job requirements satisfied; jobId=${id}; triggered=${triggered}; triggerExists=${triggerExists}; elapsedMs=${System.currentTimeMillis()-begin}")
    def satisfied = triggered || !triggerExists
    if (satisfied) {
      log.info("Job requirements satisfied; id=${id}; triggered=${triggered}; triggerExists=${triggerExists}; satisfiedTriggers=${satisfiedTriggers};")
    }
    return satisfied
  }

  def addArgIfNotPresent(args, key, value) {
    def present = args.find { it.trim().startsWith(key) } != null
    if (!present && (!properties[key] || value)) {
      args << "${key}=${value}".toString()
    }
  }
  
  public boolean isStale() {
    // Can't be stale if it's still pending or running
    if (!isCompleted()) return false
    
    // Only stale if its age exceeds the maxAgeInDays property
    def maxAgeInDays = this.properties['job.maxAgeInDays'].toInteger()
    def jobDate = completedDate ?: new Date(jobDir.lastModified())
    if (!jobDate) log.warn("Job age undeterminable, unable to detect staleness; jobId=${id}; jobState=${job.currentStatus}")
    def ageInDays = new Date() - jobDate
    return (maxAgeInDays > 0) && (ageInDays > maxAgeInDays)
  }

  public String[] loadJobDetails() {
    def args = details?.split("\n") as List
    if (!args) args = []
    args = args.collect { def s = it.trim(); s.startsWith("-") ? s[1..-1] : s}
    if (jobDir) addArgIfNotPresent(args, "job.workDir", this.jobDir.canonicalPath)
    if (projectWorkDir) addArgIfNotPresent(args, "project.workDir", this.projectWorkDir.canonicalPath)
    if (jobDir) addArgIfNotPresent(args, "junitFile", this.jobDir.canonicalPath + "/toxic.xml")
    addArgIfNotPresent(args, "job.logLevel", "info")
    if (jobDir) addArgIfNotPresent(args, "job.logFile", this.jobDir.canonicalPath + "/toxic.log")
    if (jobDir) addArgIfNotPresent(args, "job.artifactsDir", this.jobDir.canonicalPath + "/artifacts")
    addArgIfNotPresent(args, "job.logLayout", "%d [%t] %-5p %c %x- %m%n")
    addArgIfNotPresent(args, "job.maxAgeInDays", 7)
    addArgIfNotPresent(args, "job.maxCommits", 10)
    addArgIfNotPresent(args, "job.init.script.0", "")
    addArgIfNotPresent(args, "job.end.script.0", "")
    addArgIfNotPresent(args, "job.repoType", "toxic.job.GitRepository")
    addArgIfNotPresent(args, "job.repoUrl", "")
    addArgIfNotPresent(args, "job.trigger.repoCommit", "")
    addArgIfNotPresent(args, "job.trigger.startTime", "")
    addArgIfNotPresent(args, "job.imageRepoType", "toxic.job.EcrRepository")
    addArgIfNotPresent(args, "agent.formatter.1", "")
    addArgIfNotPresent(args, "job.allowConcurrentRuns", false)
    return args.findAll { it }.collect { "-" + it }.toArray(new String[0])
  }

  private String[] loadJobDetailsFromRepo(String[] args) {
    File repoPropsFile = new File(projectWorkDir, name)
    if(!repoPropsFile.exists()) {
      repoPropsFile = new File(projectWorkDir, 'toxic.job')
    }
    log.info("Attempting to load job file properties; jobName='" + name + "'; jobId='" + id + "'; filename='" + repoPropsFile.absolutePath + "'")
    if(repoPropsFile.exists()) {
      String contents = repoPropsFile.text
      if(contents) {
        String[] newArgs = contents.split("\n")
        newArgs = newArgs.findAll { it }.collect { "-" + it }.toArray(new String[0])
        newArgs = args.plus(newArgs)
        def newProps = Main.loadProperties(newArgs)
        this.properties.putAll(newProps)
        return newArgs
      }
    }
    return args
  }

  protected initialize() {
    def args = loadJobDetails()

    def newProps = Main.loadProperties(args)
    this.properties.putAll(newProps)
    this.properties.log = generateLogger()
    FileUtils.forceMkdir(projectWorkDir)
    FileUtils.forceMkdir(artifactsDir)

    log.debug("initialize(); projectWorkDir=${projectWorkDir}; artifactsDir=${artifactsDir}")
    
    attachRepository()
    updateRepository()
    args = loadJobDetailsFromRepo(args)
    callScripts("job.init.script.")

    // Load again since the init scripts likely changed the doDir path, thus we need to
    // reload the parent hierarchy .properties files that could now exist
    newProps = Main.loadProperties(args)
    this.properties.putAll(newProps)
    
    if (properties.jobManager) {
      previousJob = properties.jobManager.findLatestJob(project, JobStatus.COMPLETED)
    }
  }

  protected void updateRepository() {
    if (repo) {
      commits = repo.update()
    }
  }

  protected void attachRepository() {
    if (!repo) {
      def repoType = properties['job.repoType']
      def repoUrl  = properties['job.repoUrl']
      def repoBranch = properties['job.repoBranch'] ?: null
      def repoChangesetUrlTemplate = properties['job.repoChangesetUrlTemplate'] ?: null

      if (repoType && repoUrl && projectWorkDir) {
        FileUtils.forceMkdir(projectWorkDir)
        this.repo = SourceRepositoryFactory.make(repoType, projectWorkDir.toString(), repoUrl, repoChangesetUrlTemplate, repoBranch)
      }
    }
  }

  protected DockerImageRepository getImageRepo() {
    def imageRepoType = properties['job.imageRepoType']

    if (imageRepoType && imageRepositoryName) {
      return DockerImageRepositoryFactory.make(imageRepoType, imageRepositoryName, properties)
    }
  }

  protected String getImageRepositoryName() {
    if (properties['job.imageRepository'])
      return properties['job.imageRepository']

    if (cachedImageRepository == null) {
      (this.id =~ /^(.+)-ci\.job-[0-9]+$/).with { matcher ->
        if (matcher.matches()) {
          cachedImageRepository = matcher[0][1]
        } else {
          cachedImageRepository = ""
        }
      }
    }
    return cachedImageRepository ?: null
  }

  protected def generateLogger() {
    def logger = Logger.getLogger(this.id)
    logger.setAdditivity(false)
    logger.removeAllAppenders()

    if (this.properties['job.logLayout'] && this.properties['job.logFile']) {
      def layout = new PatternLayout(this.properties['job.logLayout'])
      logger.addAppender(new FileAppender(layout, this.properties['job.logFile']))
    }

    if (this.properties['job.logLevel']) {
      logger.setLevel(Level.toLevel(this.properties['job.logLevel']))
    }

    if (this.properties['job.splunk.loghost'] && this.properties['job.splunk.logport']) {
      def layout = new PatternLayout(this.properties['job.logLayout'])
      
      new SplunkRawTCPAppender(layout).with { appender ->
        appender.name = "splunk"
        appender.host = this.properties['job.splunk.loghost']
        appender.port = this.properties['job.splunk.logport'] as Integer
        appender.maxQueueSize = "5MB"
        appender.dropEventsOnQueueFull = false

        log.info("Adding SplunkRawTCPAppender +++ name=${appender.name}; host=${appender.host}; port=${appender.port}")
        logger.addAppender(appender)
      }
    }

    return logger
  }

  protected end() {
    callScripts("job.end.script.")

    use (NotificationCenter) {
      notify(EventType.JOB_CHANGED, this.toSimple())
    }

    // only remove if empty, to keep clutter minimzed, can also force delete via -job.end.script. arg
    projectWorkDir.delete()
  }
  
  protected callNotifications() {
    // Don't notify on a first run, otherwise a first run that fails will notify everyone that has ever committed to the repo
    if (!previousJob) return 

    this.properties.forProperties("job.notification.") { key, value ->
      try {
        Class.forName(value).newInstance().execute(this)
      } catch (Exception e) {
        log.error("Unable to execute notification; jobId=${id}; key=${key}; class=${value}; reason=${e}", log.level <= Level.DEBUG ? e : null)
      }
    }
  }

  protected callScripts(prefix) {
    this.properties.forProperties(prefix) { key, script ->
      if (!isCompleted() && script.trim()) {
        this.properties.log?.info("Script starting; key=${key}; script=${script}")
        def result = GroovyEvaluator.eval(script, this.properties)
        this.properties[key + ".result"] = result
        this.properties.log?.info("Script complete; key=${key}; result=${result}")
      }
    }
  }

  protected SerializedFormatter getFormatter() {
    def formatter = new SerializedFormatter()
    def props = new ToxicProperties()
    props.serializedFile = "${this.jobDir}/results.json.gz"
    props.serializedSummaryFile = "${this.jobDir}/summary.json.gz"
    formatter.init(props)
    return formatter
  }

  protected loadResults() {
    def summary = formatter.parseSummary()
    updateStatus(JobStatus.COMPLETED)
    if (summary) {
      this.startedDate = new Date(summary.startedTime)
      this.submittedDate = new Date(summary.submittedTime)
      this.completedDate = new Date(summary.completedTime)
      this.suites = summary.suites
      this.failed = summary.failed
      this.commits = summary.commits
    } else {
      updateStatus(JobStatus.ABANDONED)
    }
  }

  protected saveResults() {
    formatter.format(this.results)
    def summary = toSimple()
    summary.startedTime = summary.startedDate?.time
    summary.submittedTime = summary.submittedDate?.time
    summary.completedTime = summary.completedDate?.time 
    formatter.formatSummary(summary)
  }

  /**
   * Looks at most recent results (since last update) and accumulates those stats
   * into previously computed metric.
   */
  synchronized def update(long retainHistoricResultsMillis = 600000) {
    if (currentStatus == JobStatus.RUNNING) {
      def tmpResults = this.localSimpleResults.clone()
      TaskMaster.walkResults(this.agent?.taskMasters, taskMasterResultIndex) { r ->
        updateSimpleResult(tmpResults, r)
      }
      this.suites = tmpResults.size()
      this.failed = tmpResults.count { f,r -> r.find { t -> !t.success } }    
      this.localSimpleResults = tmpResults
      this.resultsLastAccessedTime = System.currentTimeMillis()

      use (NotificationCenter) {
        notify(EventType.JOB_CHANGED, this.toSimple())
      }

    } else if (isCompleted()){
      if (results && ((System.currentTimeMillis() - this.resultsLastAccessedTime) > retainHistoricResultsMillis)) {
        this.results.clear()
        this.localSimpleResults.clear()
      }
    }
  }
  
  def updateSimpleResult(tmpResults, r) {
    def simple = r.toSimple()
    def familyResults = tmpResults[simple.suite]
    if (!familyResults) {
      familyResults = []
      tmpResults[simple.suite] = familyResults
    }
    familyResults << simple   
    
    // Get the most recently completed result
    if (simple.complete && simple.stopTime > lastCompleted?.stopTime) lastCompleted = simple
  }
  
  def getSimpleResults() {
    if (!processing && !this.localSimpleResults) {
      synchronized (this) {
        if (!this.localSimpleResults) {
          results = results ?: formatter.parse()
          results.each { r ->
            updateSimpleResult(this.localSimpleResults, r)
          }
          resultsLastAccessedTime = System.currentTimeMillis()
        }
      }
    }
    return this.localSimpleResults
  }
  
  def limit(list, prop) {
    def value
    try {
      value = this.properties[prop]
      int max = Integer.parseInt(value?.toString())
      list = list?.size() > max ? list[-max..-1] : list
    } catch (Exception e) {
      log.warn("Failed to limit list size; max=${value}; reason=${JobManager.findReason(e)}")
    }    
  }
  
  public performAction(action, auth) {
    action = lookupPropertyKey(action)
    def script = this.properties[action]
    if (script) {
      def allowedAuths = collectActionAuths(action)
      auth = auth && allowedAuths ? allowedAuths.find { it.equalsIgnoreCase(auth) } : auth
      if (!allowedAuths || auth) {
        def props = this.properties.clone()
        props.actionAuth = auth
        return GroovyEvaluator.eval(script, props)
      }
    }
    return false
  }

  public void performAutoTriggerActions() {
    if (!startedDate || !properties) return

    def now = System.currentTimeMillis()
    collectValidActions().each { key, map ->
      def secondsStr = this.properties[key + ".autoTriggerAfterSeconds"]
      if (secondsStr) {
        try {
          long millis = secondsStr.toLong() * 1000
          def startTime = startedDate.time
          if (now - startTime > millis) {
            // eligible based on time, now check if it's already been triggered
            def file = new File(artifactsDir, "autoTrigger_" + name + ".txt")
            if (file.isFile()) {
              log.debug("Already executed auto trigger; key=${key}; millis=${millis}; time=${file.text}")
            } else {
              log.info("Executing auto trigger; jobId=${id}; key=${key}; millis=${millis}; time=${now}")
              def result = performAction(key, null)
              file.text = now.toString()
              log.info("Finished executing auto trigger; jobId=${id}; key=${key}; millis=${millis}; time=${now}; result=${result}")
            }
          }
        } catch (NumberFormatException nfe) {
          log.warn("Invalid auto trigger seconds value; jobId=${id}; seconds=\"${secondsStr}\"; reason=\"${nfe.toString()}\"")
        }
      }
    }
  }
  
  private lookupPropertyKey(incorrectlyCasedKey) {
    return this.properties.find { k, v -> k?.toString().equalsIgnoreCase(incorrectlyCasedKey) }?.key
  }

  def collectActionAuths(action) {
    def auths
    if (this.properties.containsKey("secure." + action + ".auths." + name)) {
      auths = this.properties["secure." + action + ".auths." + name]
    } else if (this.properties.containsKey("secure." + action + ".auths")) {
      auths = this.properties["secure." + action + ".auths"]
    } else if (this.properties.containsKey(action + ".auths." + name)) {
      auths = this.properties[action + ".auths." + name]
    } else if (this.properties.containsKey(action + ".auths")) {
      auths = this.properties[action + ".auths"]
    }
    return auths ? auths.tokenize(";,")?.sort() : null
  }
  
  public collectActions(prefix) {
    def actions = [:]
    this.properties.forProperties(prefix) { key, script ->
      def name = key.trim() - prefix
      if (!name.contains(".")) {
        actions[key] = [name:name]
      }
    }
    return actions
  }
  
  public collectValidActions() {
    def actions = [:]
    actions += collectActions("job.action.")
    if (isCompleted()) {
      actions += collectActions("job.actionIfComplete.")
      if (failed) {
        actions += collectActions("job.actionIfCompleteWithFailure.")
      } else {
        actions += collectActions("job.actionIfCompleteSuccess.")
      }
    } 
    if (failed) {
      actions += collectActions("job.actionIfFailure.")
    }
    return actions
  }

  public long getDuration() {
    return this.startedDate ? (TimeCategory.minus(this.completedDate ?: new Date(), this.startedDate).toMilliseconds()) : 0
  }

  public Map toSimple() {
    def map = [:]

    map.id              = this.id
    map.project         = this.project
    map.sequence        = this.sequence
    map.details         = this.details
    map.status          = this.currentStatus.toString()
    map.satisfiedTriggers = this.satisfiedTriggers?.join(",")
    map.submittedDate   = this.submittedDate
    map.startedDate     = this.startedDate
    map.completedDate   = this.completedDate
    map.duration        = duration
    map.suites          = this.suites
    map.failed          = this.failed
    map.logFile         = logFile?.canonicalPath
    map.logSize         = logFile?.size()
    map.commits         = limit(this.commits, 'job.maxCommits')
    map.lastCompleted   = this.lastCompleted
    map.actions         = collectValidActions()  
    map.prevFailed      = this.previousJob?.failed ?: 0
    map.tags            = this.fetchTags()
    map.group           = this.properties['job.group'] ?: ""
    map.paused          = PauseManager.instance.isProjectPaused(this.project)
    map.acked           = AckManager.instance.getAck(this.id)
    map.icon            = this.properties['job.icon'] ?: ""

    // This method must be quick; please don't add code that will cause it to take excessive time to complete.

    return map
  }

  public boolean isUpdatedSince(Date since) {
    boolean retVal = true

    if (since) {
      if (isCompleted()) {
        retVal = since <= completedDate
      } else {
        retVal = true
      }
    }

    return retVal
  }

  public List toSuiteBreakdown(long fromMillis, long toMillis) {
    def res = simpleResults.collect { suite, tasks ->
      def et = tasks.findAll { it.stopTime >= fromMillis &&
                               it.stopTime <= toMillis }
      !et ? null :
      [
        suite:     suite,
        family:    et.first().family,
        tasks:     et.size(),
        complete:  et.every { t -> t.complete },
        success:   et.every { t -> t.success },
        startTime: et.first().startTime,
        duration:  et.collect { it.duration }.sum(),
      ]
    }    
    return res?.findAll { it }
  }

  public List toTaskBreakdown(String suite) {
    return simpleResults[suite]
  }
  
  public File getLogFile() {
    return this.properties['job.logFile'] ? new File(this.properties['job.logFile']) : null
  }

  public File getReadMeFile() {
    def readme = new File(this.projectWorkDir, "README.md")
    if (!readme.exists()) readme = new File(this.projectWorkDir, "readme.md") 
    if (!readme.exists()) readme = new File(this.projectWorkDir, "README.txt") 
    if (!readme.exists()) readme = new File(this.projectWorkDir, "readme.txt") 
    readme
  }

  public def fetchArtifacts() {
    def artifacts = []
    if (artifactsDir?.isDirectory()) {
      artifactsDir.eachFile {
        if (!it.isDirectory()) artifacts << [name: it.name, size: it.size()]
      }
    }
    return artifacts
  }

  public def fetchArtifact(filename) {
    def file = new File(artifactsDir, filename)
    if (file?.isFile()) {
      return file.newInputStream()
    }
    return null
  }

  public def fetchTags() {
    String ext = '.tag'
    
    fetchArtifacts()
      .findAll { artifact -> artifact.name.endsWith(ext) && artifact.name != ext }
      .collect { artifact -> artifact.name - ext }
  }

  public boolean hasTag(String tag) {
    fetchTags().any { t -> t == tag }
  }

  public String fetchLog() {
    if (logFile?.isFile()) {
      return logFile.text
    }
    return null
  }

  public Map fetchLog(int offset, int max) {
    def resp = ""
    int count = 0
    int remaining = 0
    if (logFile?.isFile()) {
      logFile.withInputStream {
        if (offset && offset > 0 && logFile.size() >= offset) {
          it.skip(offset)
        } else {
          offset = 0
        }
        def c = 0
        max = max > 0 ? max : 4096
        def bytes = new byte[max]
        count = it.read(bytes, 0, max)
        if (count > 0) {
          remaining = it.available()
          resp = new String(bytes, 0, count)
        }
      }
    }
    return [log:resp,size:count,offset:offset,remaining:remaining,job:toSimple()]
  }

  public String logHead(int numLines) {
    def out=new StringBuilder()
    if (logFile?.isFile()) {
      def lineCount = 0
      logFile.withReader {
        while(lineCount < numLines) {
          def line = it.readLine()
          if(line) { out.append(line).append("\n") }
          lineCount++;
        }
      }
    }
    return out.toString()
  }

  public String logTail(int numLines) {
    def out=new StringBuilderWriter()
    if (logFile?.isFile()) {
      Tail.tailFile(logFile.absolutePath, numLines, out)
    }
    return out.toString()
  }

  public String getProject() {
    if (cachedProject == null) {
      (this.id =~ /^(.*)-[0-9]+$/).with { matcher ->
        cachedProject = matcher ? matcher[0][1] - ".job" : this.id
      }
    }
    return cachedProject
  }

/**
   * Returns the task results as a hierarchical map.
   * Ex:
   * [ name: 'root',
   *   children: [
   *     [ id: 'toxic/', name: 'toxic',
   *       children: [
   *        [ id: '0-0-01', name: '05_global.properties', duration: 2, ... ],
   *        [ id: 'toxic/10_foo', name: '10_foo',
   *          children: [ .... ]
   *        ]
   *     ]
   *   ]
   * ]
   */
  def getTaskResultsHierarchy() {
    def tasks = this.simpleResults.collect { suite, tasks -> tasks }.flatten()

    def hierarchy = [name: 'root', children: []]
    tasks.each { task ->
      def child = hierarchy

      def findOrCreateChild = { pathToChild, childName ->
        def nextChild = child.children.find { it.name == childName }
        if (!nextChild) {
          nextChild = [id: pathToChild, name: childName, children: []]
          child.children << nextChild
        }
        nextChild
      }

      // Navigate the hierachy
      def path = ""
      task.family?.split("/").each { part ->
        path += "${part}/"
        child = findOrCreateChild(path, part)
      }

      // Add this task
      child = findOrCreateChild("${path}${task.name}", task.name)
      child.remove('children')
      child << task
    }
    hierarchy
  }


  public int getSequence() {
    if (cachedSequence == null) {
      (this.id =~ /^.*-([0-9]+)$/).with { matcher ->
        cachedSequence = matcher ? matcher[0][1] : "0"
      }
    }
    return cachedSequence.toInteger()
  }

  public Date now() {
    return new Date()
  }

  public boolean isPending() {
    this.currentStatus == JobStatus.PENDING
  }

  public boolean isRunning() {
    this.currentStatus in [JobStatus.INITIALIZING, JobStatus.RUNNING, JobStatus.ENDING]
  }

  public boolean isCompleted() {
    this.currentStatus in [JobStatus.COMPLETED, JobStatus.ABANDONED]
  }

  public boolean isSuccessful() {
    isCompleted() && failed == 0
  }

  public void discard() {
    discard = true
  }

  public boolean shouldDiscard() {
    return discard
  }

  public void repeat() {
    repeat = true
  }

  public boolean shouldRepeat() {
    return repeat
  }

  public def halt() {
    if (!isCompleted()) {
      log.info("Halting job agent; jobId=${id}")
      this.agent?.shutdown()
      def abortedResult = new TaskResult(id, "job aborted", "Aborted", this.class.name)
      abortedResult.success = false
      results << abortedResult
    }
    return true
  }

  protected void updateStatus(status) {
    if (this.currentStatus != JobStatus.ABANDONED) this.currentStatus = status
  }
  
  int compareTo(Object other) {
    int result = 0
    if (other instanceof Job) {
      if (this.project < other.project) {
        result = -1
      } else if (this.project > other.project) {
        result = 1
      } else if (this.project == other.project) {
        if (this.sequence < other.sequence) {
          result = -1
        } else if (this.sequence > other.sequence) {
          result = 1
        }
      }
    } else {
      result = -1
    }
    return result
  }

  def parseJUnitXmlFilesInDir(File dir) {
    def stats = new JUnitParser().parseDir(dir, results)
    suites += stats.suites
    failed += stats.failures
  }
  
  String getMutex() {
    return this.properties?.mutex ?: ""
  }
}

public enum JobStatus { PENDING, INITIALIZING, RUNNING, ENDING, COMPLETED, ABANDONED }

package toxic.job

import toxic.*

import org.apache.log4j.*
import java.util.concurrent.*
import java.nio.file.*
import org.apache.commons.io.*
import groovy.time.TimeCategory
import toxic.notification.*

public class JobManager implements Runnable,Publisher {
  protected static Logger log = Logger.getLogger(JobManager.class)

  public static considerOnlySuccessfulJobsForAvgDuration = true
  public static groupCommitsForUsersWithSimilarNames = true

  private ToxicProperties mgrprops
  private boolean running = false
  private boolean shutdownSoon = false
  private int pollInterval
  private String jobDirectory
  private List<Job> jobs = []
  private List<Job> archive = []
  private ThreadPoolExecutor jobPool
  private String propertiesUrl
  private long propertiesRefreshInterval = 60000
  private long lastPropertiesRefreshTime
  private long purgeInterval = 600000
  private ToxicProperties secureProps
  private def mainThread
  private SourceRepository configRepo
  private def urlCache = [:]
  protected long urlCacheExpireMs = 600000

  public JobManager(String propertiesUrl, String defaultJobDir = null) {
    this.propertiesUrl = propertiesUrl
    this.jobDirectory = defaultJobDir
    this.mgrprops = new ToxicProperties()
    this.secureProps = new ToxicProperties()
    def factory = new ThreadFactory() {
      int count = 0
      public Thread newThread(Runnable r) {
        return new Thread(r, "job-${count++}");
      }
    }
    this.jobPool = new ThreadPoolExecutor(1, 1, 60000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), factory)
  }
  
  def loadSecureProperties(String filename) {
    if (filename) {
      def props = new ToxicProperties()
      int count = 0
      def txt = new File(filename).text
      txt.split("\n").each { 
        count++
        it = it.trim()
        def idx = it.indexOf("=")
        if (idx > 0) {
          def key = it.substring(0, idx)
          def value = it.substring(idx + 1)
          props[key] = value
        } else {
          log.warn("Invalid secure property detected; filename=${filename}; line=${count}")
        }
      }
      synchronized(this) {
        this.secureProps = props
      }
      log.info("Loaded secure properties; filename=${filename}; count=${count}")
    }
  }
  
  def addPropertyIfNotPresent(props, prop, value) {
    if (!props.containsKey(prop)) {
      props[prop] = value
    }
  }

  public void refreshConfigRepo() {
    if (!configRepo) {
      def type = mgrprops.configRepoType ?: secureProps.configRepoType 
      def url = mgrprops.configRepoUrl ?: secureProps.configRepoUrl
      if (type && url) {
        log.info("Attaching config repository; type='${type}'; url='${url}'")
        FileUtils.forceMkdir(new File(configRepoDir))
        configRepo = SourceRepositoryFactory.make(type, configRepoDir, url, null)
      } else {
        log.debug("No config repository defined; will not update properties from repository")
      }
    }
    configRepo?.update()
  }
  
  public void refreshProperties() {
    log.trace("Starting to refresh properties")
    def begin = System.currentTimeMillis()
    def now = new Date().time
    if (now - this.lastPropertiesRefreshTime > this.propertiesRefreshInterval) {
      synchronized(this) {
        refreshConfigRepo()

        def newProps = mgrprops
        this.lastPropertiesRefreshTime = new Date().time
        if (propertiesUrl) {
          try {
            log.debug("Refreshing default properties from URL; url=${propertiesUrl}")
            newProps = new ToxicProperties()
            newProps.load(fetchUrlInputStream(propertiesUrl))
          } catch (Exception e) {
            log.error("Unable to refresh properties; url=${propertiesUrl}; reason=${findReason(e)}", e)
          }
        }
        
        addPropertyIfNotPresent(newProps, 'jobManager.pollInterval', 10000)
        addPropertyIfNotPresent(newProps, 'jobManager.jobDirectory', this.jobDirectory)
        addPropertyIfNotPresent(newProps, 'jobManager.propertiesRefreshInterval', 60000)
        addPropertyIfNotPresent(newProps, 'jobManager.maxConcurrentJobs', 4)
        addPropertyIfNotPresent(newProps, 'jobManager.logBufferLimit', 100000)
        addPropertyIfNotPresent(newProps, 'jobManager.purgeHistoricJobDetailsFromMemoryAfterMillis', 600000)
        addPropertyIfNotPresent(newProps, 'jobManager.urlCacheExpireMs', 60000)
        addPropertyIfNotPresent(newProps, 'project.minJobRetention', 5)
    
        this.pollInterval = newProps['jobManager.pollInterval'].toLong()
        this.jobDirectory = newProps['jobManager.jobDirectory'] 
        this.purgeInterval = newProps['jobManager.purgeHistoricJobDetailsFromMemoryAfterMillis'].toLong()
        this.propertiesRefreshInterval = newProps['jobManager.propertiesRefreshInterval'].toLong()
        this.urlCacheExpireMs = newProps['jobManager.urlCacheExpireMs'].toLong()
        this.jobPool.corePoolSize = newProps['jobManager.maxConcurrentJobs'].toInteger()    
        this.jobPool.maximumPoolSize = this.jobPool.corePoolSize
        
        newProps.jobManager = this
        newProps.eventManager = EventManager.instance
        this.mgrprops = newProps
        
        if (!EventManager.instance.isInitialized()) {
          EventManager.instance.init(eventDir)
        }

        PauseManager.instance.load(this)
        AckManager.instance.load(this)

        def dir = new File(pendingDir) 
        if (!dir.exists()) {
          log.info("Creating pending directory; pendingDir=${dir.canonicalPath}")
          dir.mkdirs()
          if (!dir.isDirectory()) {
            throw new IOException("Failed to create pending directory; pendingDir=${dir.canonicalPath}")
          }
        }

        log.trace("Finished refreshing properties; elapsedMs=${System.currentTimeMillis()-begin}")
      }
    }
  }

  public synchronized def jobProperties() {
    def jobProps = new ToxicProperties()
    // Add the secure properties (prefixed)
    secureProps.clone().each { k, v -> jobProps[ToxicProperties.SECURE_PREFIX + k]=v }

    // Lay the manager properties on top
    jobProps.putAll(this.mgrprops.clone())

    return jobProps
  }

  public void run() {
    mainThread = Thread.currentThread()
    this.running = true
    refreshProperties()
    if (currentProperties().logLevel) {
      log.getRootLogger().setLevel(Level.toLevel(currentProperties().logLevel))
    }
    fetchHistoricJobs(runningDir).each { job -> completeJob(job) }
    fetchHistoricJobs(completedDir).each { job -> archiveJob(job) }

    use (NotificationCenter) {
      notify(EventType.JOBS_LOADED, [projects:browseProjects()])
    }

    log.info("Job monitoring started; jobDir=${jobDirectory}; interval=${pollInterval};")
    runLowPriorityAsyncMaintenanceTasks()
    runHighPriorityAsyncMaintenanceTasks()
    long lastPollTime = 0
    while(running) {
      try {
        long now = System.currentTimeMillis()
        if (now - lastPollTime > pollInterval) {
          log.debug("Looking for new jobs; jobDir=${jobDirectory}; interval=${pollInterval};")
          findNewJobs()
          lastPollTime = now
        }        
        
        if (shutdownSoon && !fetchProcessingJobs()) {
          running = false
        } else {
          updateJobs()
          try { Thread.sleep(5000) } catch (Exception e) {}
        }
      } catch (Exception e) {
        log.error("Unexpected error in job manager thread; reason=${findReason(e)}", e)
      }
    }
    try { jobPool?.shutdownNow() } catch (Exception e) {}
    log.info("Job monitoring halted")
  }

  private def findNewJobs() {
    log.trace("Starting to find new jobs")
    def begin = System.currentTimeMillis()

    fetchNewJobs().each { jobFile -> addJob(jobFile) }
    log.trace("Finished finding new jobs elapsedMs=${System.currentTimeMillis()-begin}")
  }

  public void runHighPriorityAsyncMaintenanceTasks() {
    Thread.startDaemon("hi-pri-maint") {
      while (running) {
        try {
          fetchAutomatedJobs().each { name, details -> queueJob(name, details) }
          fetchCompletedJobs().each { job -> completeJob(job) }
          fetchLatestJobs().each { job -> job.performAutoTriggerActions() }
          refreshProperties()
        } catch (Exception e) {
          log.error("High priority async maintenance thread detected unexpected condition; will resume after briefy pause; reason=${e}", e)
        }
        sleep(pollInterval)
      }
    }
  }

  public void runLowPriorityAsyncMaintenanceTasks() {
    Thread.startDaemon("lo-pri-maint") {
      while (running) {
        try {
          fetchStaleJobs().each { job -> discardJob(job) }
        } catch (Exception e) {
          log.error("Low priority async maintenance thread detected unexpected condition; will resume after briefy pause; reason=${e}", e)
        }
        sleep(pollInterval)
      }
    }
  }

  def updateJobs() {
    log.trace("Starting to update jobs")
    def begin = System.currentTimeMillis()
    allJobs().each { job -> job.update(purgeInterval) }
    log.trace("Finished updating jobs; elapsedMs=${System.currentTimeMillis()-begin}")
  }
  
  /**
   * Automated jobs can be specified in the initial properties, as follows:
   * jobManager.autoJobUrl.0=http://intranet.techco.com/jobs/DailyUpdate.job
   * jobManager.autoJobUrl.5=http://intranet.techco.com/jobs/TriggerOnPush.job
   */
  public Map<String, String> fetchAutomatedJobs() {
    log.trace("Starting to fetch automated jobs")
    def begin = System.currentTimeMillis()
    def map = new HashMap<String, String>()
    this.mgrprops?.forProperties("jobManager.autoJobUrl.") { name, url ->
      try {
        def jobName = url
        def idx = url.lastIndexOf("/")
        if (idx > 0) jobName = jobName[(idx+1)..-1]
        map[jobName] = fetchUrlText(url)
      } catch (Exception e) {
        log.error("Failed to retrieve job from URL; url=${url}; reason=${findReason(e)}", e)
      }
    }
    log.trace("Finished fetching automated jobs; elapsedMs=${System.currentTimeMillis()-begin}")
    return map
  }

  public List<Job> fetchHistoricJobs(def archiveDir) {
    log.trace("Starting to fetch historic jobs")
    def begin = System.currentTimeMillis()
    List<Job> results = [] as List<Job>
    def comDir = new File(archiveDir)
    if (comDir.isDirectory()) {
      comDir.eachDir { jobDir ->
        results << inflateJob(jobDir)
      }
    }
    log.trace("Finished fetching historic jobs; elapsedMs=${System.currentTimeMillis()-begin}")
    return results
  }
  
  def findJobFilename(jobDir) {
    return jobDir.name.contains("-") ? jobDir.name[0..(jobDir.name.lastIndexOf("-"))-1] : ""
  }

  public Job findJobByProject(String projectId) {
    def project = browseProjects().find { it.project == projectId }
    return findJob(project?.id)
  }

  protected String resolveFileDetails(File file) {
    log.trace("Starting to resolve file details; file=${file}")
    def begin = System.currentTimeMillis()

    def origDetails = file?.isFile() ? file.text : ""
    def jobDetails = resolveDetails(origDetails)
    if (origDetails?.trim() != jobDetails?.trim()) {
      file.text = jobDetails
    }

    log.trace("Finished resolving file details; elapsedMs=${System.currentTimeMillis()-begin}")
    return jobDetails
  }
  
  protected String resolveDetails(String details) {
    // Include dependent properties if specified
    if (details?.trim()) {
      def replacedDetails = new StringBuffer()
      def tempProps = new ToxicProperties()
      tempProps.load(new StringReader(details))
      tempProps.forProperties("job.include.") { key, url ->
        log.debug("Loading included properties; key=${key}; url=${url}")
        try {
          def contents = fetchUrlText(url)
          if (contents) {
            replacedDetails.append(contents)
            replacedDetails.append("\n")
          }
        } catch(IOException e) {
          log.warn("Unable to retrieve included details; url=${url}; reason=${e}", e)
        }
      }
      replacedDetails.append(details)
      return replacedDetails.replaceAll("([^#])job.include.", "\$1#job.include.");
    }
    return details
  }

  public Job inflateJob(File jobDir) {
    def details = "CORRUPT JOB: ${jobDir.canonicalPath}"
    def jobFilename = findJobFilename(jobDir)
    def jobFile = new File(jobDir, jobFilename)    
    details = resolveFileDetails(jobFile) ?: details
    def job = new Job(jobDir.name, jobDir, new File(projectDir, jobFilename), details, jobProperties())
    job.loadResults()
    return job
  }

  public synchronized void archiveJob(Job job) {
    log.debug("Archived job; jobId=${job.id}")
    archive << job
  }

  public List<Job> fetchProcessingJobs() {
    currentJobs().findAll { it.processing }
  }

  public List<Job> fetchCompletedJobs() {
    currentJobs().findAll { it.isCompleted() && !it.processing }
  }

  public List<Job> fetchLatestJobs() {
    def all = allJobs().sort()
    def projectJobs = [:]
    all.reverseEach {
      def job = projectJobs[it.project]
      if (!job) {
        projectJobs[it.project] = it
      }
    }
    def results = [] as List<Job>
    projectJobs.each { k, v -> results << v }
    return results.sort { a, b -> a.id <=> b.id }
  }

  public synchronized def removeJob(Job job) {
    jobs.remove(job)
  }

  public void completeJob(Job job) {
    removeJob(job)
    if (!job.shouldDiscard()) {
      def dest = new File(completedDir)
      try {
        FileUtils.moveToDirectory(job.jobDir, dest, true)
      } catch (Exception e) {
        log.warn("Forcing deletion of historic job from running directory; jobId=${job.id}; reason=${findReason(e)}", e)
        job.jobDir.deleteDir()
      }
      archiveJob(inflateJob(new File(job.id, dest)))
    } else {
      job.jobDir.deleteDir()
    }
    
    if (job.shouldRepeat()) {
      queueJob(job.name, job.details)
    }
  }
  
  public List<Job> fetchStaleJobs() {
    log.trace("Starting to fetch stale jobs")
    def begin = System.currentTimeMillis()

    def staleJobs = []
    // return just one job at a time to avoid pruning too aggresively, otherwise
    // we could end up with fewer jobs that minJobRetention requires.  
    def job = archivedJobs().find { 
      def relatedJobs = browseJobsUnsimplified(it.project).findAll { it.isStale() }
      if (relatedJobs.size() > this.mgrprops['project.minJobRetention'].toInteger()) {
        return it.isStale() 
      }
      return false
    }
    if (job) staleJobs << job
    log.trace("Finished fetching stale jobs; elapsedMs=${System.currentTimeMillis()-begin}")    
    return staleJobs
  }

  public void discardJob(Job job) {
    log.info("Discarding stale job; jobId=${job.id}")
    synchronized(this) {
      archive.remove(job)
    }
    if (job.jobDir?.isDirectory()) job.jobDir?.deleteDir()
  }

  public List<File> fetchNewJobs() {
    log.trace("Starting to fetch new jobs")
    def begin = System.currentTimeMillis()

    def newJobs = []
    def pending = new File(pendingDir)

    pending.eachFileMatch(~/.*.job/) { f -> newJobs << f }

    log.trace("Finished fetching new jobs; elapsedMs=${System.currentTimeMillis()-begin}")
    newJobs
  }

  public File queueJob(String name, String details) {
    File file = new File(pendingDir, name)
    file.text = details
    return file
  }
  
  public String runJob(Job job) {
    if (!job) return null
    
    def jobFilename = findJobFilename(job.jobDir)
    def jobFile = new File(pendingDir, jobFilename)
    
    if (!jobFile.exists()) {
      jobFile = new File(job.jobDir, jobFilename)
    }

    PauseManager.instance.unpauseProject(this, job.project)
    
    return addJob(jobFile, false)
  }
  
  def similarJobsExist(job) {
    log.trace("Starting detection of similar job existence; job=${job?.id}")
    def begin = System.currentTimeMillis()
    def exist = browseJobsUnsimplified(job.project).size() > 0
    log.trace("Finished detecting if similar jobs exist; exist=${exist}; elapsedMs=${System.currentTimeMillis()-begin}")
    return exist
  }

  def jobDefinitionChanged(job) {
    log.trace("Starting detection of job definition change; job=${job?.id}")
    def begin = System.currentTimeMillis()
    def changed = false
    def previousJobs = browseJobsUnsimplified(job.project)
    if(previousJobs) {
      def lastJob = previousJobs.last()
      if(job.details && (job.details != lastJob.details)) {
        log.info("Job details have changed since last run of project ${job.project}")
        changed = true
      }
    } else {
      log.info("No prior jobs for project ${job.project}")
      changed = true;
    }
    log.trace("Finished detecting job definition change; changed=${changed}; elapsedMs=${System.currentTimeMillis()-begin}")
    return changed
  }

  public synchronized boolean addJob(Job job) {
    if (jobs.find { it.id == job.id }) {
      log.warn("Will not add duplicate job; id=${job.id}")
      return false
    }
    jobs.add(job)
    return true
  }

  public String addJob(File file, boolean firstRun = true) {
    log.trace("Starting to add job; file=${file}")
    def begin = System.currentTimeMillis()
    if (!file.isFile()) {
      throw new Exception("Unsupported job file cannot be added; file=${file}")
    }

    def jobId = generateUniqueJobId(file.name)
    def jobDir = new File(runningDir, jobId)
    def jobDetails = resolveFileDetails(file)

    def job = new Job(jobId, jobDir, new File(projectDir, file.name), jobDetails, jobProperties())
    
    // If this job has never run then run it now, don't wait for a trigger.
    job.runningRelatedJobs = currentJobs().count { it.project == job.project }
    def similarExist = similarJobsExist(job)
    def reqsSatisfied = job.requirementsSatisfied(firstRun)
    if (shutdownSoon) {
      log.info("Shutting down soon, will not add new job; jobId=${job.id}")
      jobId = null
    } else if ((!similarExist || reqsSatisfied)
      && !PauseManager.instance.isProjectPaused(job.project)
      && !job.concurrencyLimitReached()
      && isEnvironmentAvailable(job.getEnvironment())) {
      FileUtils.forceMkdir(jobDir)
      if (firstRun) {
        FileUtils.moveFileToDirectory(file, jobDir, true)
      } else {
        FileUtils.copyFileToDirectory(file, jobDir, true)
      }

      if (addJob(job)) {
        log.info("Adding new job; jobId=${job.id}; similarExist=${similarExist}; reqsSatisfied=${reqsSatisfied}")
        jobPool.submit(job)
      } else {
        jobId = null
      }
    } else {
      log.debug("Job requirements not satisfied; jobId=${jobId}")
      jobId = null
    }
    log.trace("Finished adding job; elapsedMs=${System.currentTimeMillis()-begin}")
    return jobId
  }

  public String generateUniqueJobId(String baseId) {
    // Count how many times this job appears in running or completed subdirs
    def max = -1
    def runDir = new File(runningDir)
    def comDir = new File(completedDir)
    if (runDir.isDirectory()) runDir.eachDirMatch( ~/^${baseId}-[0-9]+$/ ) { max = Math.max(getJobNum(it.name), max)}
    if (comDir.isDirectory()) comDir.eachDirMatch( ~/^${baseId}-[0-9]+$/ ) { max = Math.max(getJobNum(it.name), max)}
    max++

    return baseId + "-" + max
  }

  public int getJobNum(name) {
    def num = 0
    if (name.contains("-")) {
      num = name[(name.lastIndexOf("-") + 1)..-1]
    }
    return num.toInteger()
  }

  public def browseProjects(Date since = null) {
    def latestJobs = fetchLatestJobs()
    return latestJobs.findAll { it.isUpdatedSince(since) || PauseManager.instance.hasProjectPauseToggledSince(since, it.project) }.collect { 
      def map = it.toSimple()
      map + [paused:PauseManager.instance.isProjectPaused(map.project), acked:AckManager.instance.getAck(it.id)]
    }
  }

  public def browseJobs() {
    allJobs().sort().collect { it.toSimple() }
  }

  public def browseJobsUnsimplified(String project) {
    allJobs().sort().findAll { j -> j.project?.equalsIgnoreCase(project) }
  }

  public def browseJobs(String project) {
    browseJobsUnsimplified(project).collect { it.toSimple() }
  }
  
  public def findLatestJob(String project, JobStatus status=null) {
    def jobs = browseJobs(project)

    while (jobs) {
      def job = jobs.pop()

      if (status) {
        if (job.status == status.toString()) {
          return job
        }
      }
      else {
        return job
      }
    }
  }

  public findJobByFuzzyId(match) {
    if (match == null || match.size() < 3) return []

    def foundJobs = allJobs().findAll { job ->
      job.id ==~ /.*${match}.*/
    }.sort { job1, job2 ->
      def id1 = getJobNum(job1.id).toInteger() 
      def id2 = getJobNum(job2.id).toInteger()

      id1 <=> id2
    }

    foundJobs ? foundJobs.last() : foundJobs
  }

  public def findJob(id) {
    allJobs().find { it.id?.equalsIgnoreCase(id) }
  }

  public synchronized def allJobs() {
    (jobs + archive)
  }

  public synchronized def archivedJobs() {
    return archive.clone()
  }

  public synchronized def currentJobs() {
    return jobs.clone()
  }

  public List getRunningJobs() {
    getJobsByStatus(JobStatus.RUNNING)
  }

  public List getPendingJobs() {
   getJobsByStatus(JobStatus.PENDING)
  }

  public List getCompletedJobs() {
    getJobsByStatus(JobStatus.COMPLETED)
  }

  protected List getJobsByStatus(JobStatus status) {
   currentJobs().findAll { j -> j.currentStatus == status }
  }

  protected String getProjectDir() {
    jobDirectory + "/project"
  }

  protected String getPendingDir() {
    jobDirectory + "/pending"
  }

  protected String getRunningDir() {
    jobDirectory + "/running"
  }

  protected String getCompletedDir() {
    jobDirectory + "/completed"
  }
  
  protected String getEventDir() {
    jobDirectory + "/event"
  }

  protected String getConfigDir() {
    jobDirectory + "/config"
  }

  protected String getConfigRepoDir() {
    jobDirectory + "/config/repo"
  }

  def currentProperties() {
    return this.mgrprops
  }

  private cloneRepository(String repoUri, String targetDir) {
    log.info("Cloning repository +++ repo=${repoUri}; target=${targetDir}")

    "hg clone ${repoUri} ${targetDir}"
      .execute()
      .waitForProcessOutput(System.out, System.err);
  }
  
  public static String findReason(Throwable e) {
    if (e.cause) {
      return findReason(e.cause)
    }
    return e.toString()
  }
  
  def lookupSecureValue(fullKey) {
    return this.secureProps.find { k, v -> fullKey.startsWith(k) }?.value
  }

  def prepareLocalUrl(input) {
    if (!input.contains("://")) {
      input = "file://" + configRepoDir + "/" + input
      if (!configRepo) {
        log.warn("Attempting to reference local path within config repo, however config repo is not configured; input='${input}'")
      }
    }
    return input    
  }
  
  public def fetchUrlInputStream(input) {
    input = prepareLocalUrl(input)
    def url = new URL(input).openConnection()
    def auth = lookupSecureValue(input)
    if (auth) {
      auth = "Basic " + auth.bytes.encodeBase64().toString()
      url.setRequestProperty("Authorization", auth)
    }
    log.debug("Connecting to URL; url=${input}; auth=${auth != null}")
    return url.inputStream
  }
  
  public def fetchUrlText(url) {
    log.trace("Starting to fetch url; url=${url}")
    def begin = System.currentTimeMillis()
    def value = cached(url)
    if (!value) {
      value = fetchUrlInputStream(url).text
      cache(url, value)
    }
    log.trace("Finished fetching url; elapsedMs=${System.currentTimeMillis()-begin}")
    return value
  }
  
  public def cache(url, value) {
    synchronized (urlCache) {
      urlCache[url] = [value:value,time:System.currentTimeMillis()]
    }
  }
  
  public def cached(url) {
    def value
    synchronized (urlCache) {
      def map = urlCache[url]
      if (map) {
        def elapsed = System.currentTimeMillis() - map.time
        if (elapsed < this.urlCacheExpireMs) {
          value = map.value
        }
      }
    }
    return value  
  }
  
  public String getLogContents() {
    def logFile = log.rootLogger?.allAppenders?.find { it instanceof FileAppender }?.file
    if (logFile) {
      def file = new File(logFile)
      if (file.isFile()) {
        def text = file.text
        def limit = this.mgrprops["jobManager.logBufferLimit"].toInteger()
        if (text.size() > limit) {
          text = text.substring(text.size() - limit)
        }
        return text
      }
    }    
    return ""
  }

  public Map generateMetrics(Date cutOffDate) {
    def results = [:]
    results.totalJobs = 0
    results.failedJobs = 0

    def commitCache = [:]

    def dailyMetrics = [:]
    def commitMetrics = [:]
    def projectMetrics = [:]
    allJobs().each {
      if (it.completedDate && it.completedDate > cutOffDate) {
        // Collect metrics by day
        def date = it.completedDate.format("yyyy-MM-dd")
        def map = dailyMetrics[date]
        if (!map) {
          map = [failedJobs:0, totalJobs:0, totalDuration:0, successDuration: 0]
          dailyMetrics[date] = map
        }
        map.failedJobs += (it.failed > 0 ? 1 : 0)
        map.totalJobs++
        map.totalDuration += (it.duration ?: 0)
        map.successDuration += (it.failed > 0 ? 0 : it.duration)

        // Collect metrics by committer
        it.commits?.each { commit ->
          commit.user = getCommitUser(commit)
          def committerMetrics = commitMetrics[commit.user]
          if (!committerMetrics) {
            committerMetrics = [failedJobs:0, totalCommits:0]
            commitMetrics[commit.user] = committerMetrics
          }

          // have we seen this commit before?
          if (!commitCache.containsKey(commit.changeset)) {
            // initialize this commit with no known failures attributed
            commitCache[commit.changeset] = false

            // Only give credit for the commit once, even if this commit appears in multiple projects
            committerMetrics.totalCommits++
          }

          // Only increment failure count if this commit hasn't already been blamed on this committer for another project
          if (it.failed > 0) {
            if (!commitCache[commit.changeset]) {
              committerMetrics.failedJobs++
            }
            // Cache that we've dinged this committer for a job failure so that we don't play double jeopardy on him/her.
            commitCache[commit.changeset] = true
          }
        }

        // Collect metrics by project
        def projectMap = projectMetrics[it.project]
        if (!projectMap) {
          projectMap = [failedJobs:0, totalJobs:0, totalDuration: 0, successDuration: 0]
          projectMetrics[it.project] = projectMap
        }
        projectMap.failedJobs += (it.failed > 0 ? 1 : 0)
        projectMap.totalJobs++
        projectMap.totalDuration += (it.duration ?: 0)
        projectMap.successDuration += (it.failed > 0 ? 0 : it.duration)

        results.totalJobs++
        results.failedJobs += (it.failed > 0 ? 1 : 0)
        results.totalJobs++
      }
    }

    // Computer metrics per day
    def dailyMetricsList = []
    dailyMetrics.sort().each { k, v ->
      v.avgDuration = calculateAvgDuration(v)
      v.remove('successDuration')
      dailyMetricsList << [date:k] + v
    }
    results.dailyMetrics = dailyMetricsList

    // Compute metrics per committer
    commitMetrics.each { user, map ->
      map.successRatio = map.totalCommits > 0 ? (map.totalCommits - map.failedJobs) / map.totalCommits : 0
      map.failRatio = map.totalCommits > 0 ? map.failedJobs / map.totalCommits : 0
    }
    results.commitMetrics = commitMetrics

    // Compute metrics per project
    projectMetrics.each { project, map ->
      map.successRatio = map.totalJobs > 0 ? (map.totalJobs - map.failedJobs) / map.totalJobs : 0
      map.failRatio = map.totalJobs > 0 ? map.failedJobs / map.totalJobs : 0
      map.avgDuration = calculateAvgDuration(map)
      map.remove('successDuration')
    }
    results.projectMetrics = projectMetrics

    return results
  }

  public Map getMetrics() {
    def metrics = [
      toxic_jobs_pending:0, 
      toxic_jobs_running:0, 
      toxic_jobs_succeeded:0, 
      toxic_jobs_failed:0,
      toxic_jobs_completed:0, 
      toxic_jobs_total:0
    ]

    def jobs = allJobs()

    jobs.each { job ->
      if (job.currentStatus == JobStatus.RUNNING) {
        metrics['toxic_jobs_running']++
      }
      if (job.currentStatus == JobStatus.PENDING) {
        metrics['toxic_jobs_pending']++
      }
      if (job.currentStatus == JobStatus.COMPLETED) {
        if (job.failed) {
          metrics['toxic_jobs_failed']++
        } else {
          metrics['toxic_jobs_succeeded']++
        }
        metrics['toxic_jobs_completed']++
      }

      metrics['toxic_jobs_total']++
    }

    return metrics
  }

  private def getCommitUser(commit) {
    if (groupCommitsForUsersWithSimilarNames){
      return getCommitUserUnmodified(commit)
        .toLowerCase()
        .replace(' ', '.')
    }

    return getCommitUserUnmodified(commit)
  }

  private def getCommitUserUnmodified(commit){
    return (commit?.user ?: "unknown")
  }

  private def calculateAvgDuration(map) {
    def totalJobs = map.totalJobs
    def totalDuration = map.totalDuration

    if (considerOnlySuccessfulJobsForAvgDuration){
      totalJobs = map.totalJobs - map.failedJobs
      totalDuration = map.successDuration
    }

    return calculateAvgDuration(totalJobs, totalDuration)
  }

  private def calculateAvgDuration(jobCount, totalDuration){
    return (jobCount > 0 ? (totalDuration / jobCount) : 0) / 60000
  }

  def shutdown(boolean forceNow = false) {
    if (forceNow) {
      running = false
      try { mainThread?.interrupt() } catch (Exception e) {}
    } else {
      shutdownSoon = true
    }
  }

  def cancelShutdown() {
    if (isManagerRunning()) {
      shutdownSoon = false
      return true
    }
    return false
  }

  boolean isShuttingDown() {
    return shutdownSoon
  }

  boolean isManagerRunning() {
    return running
  }

  boolean isEnvironmentAvailable(String environment) {
    if (environment) {
      return !getRunningJobs().find { environment.equalsIgnoreCase(it.getEnvironment()) }
    }
    return true
  }
}

package toxic.web

import log.Log
import toxic.*
import toxic.job.*
import toxic.user.*
import toxic.notification.*
import toxic.slack.*

import spark.*
import toxic.webhook.UpsourceDiscussionEvent

import java.io.*
import java.net.*
import java.util.zip.*
import groovy.json.*
import java.lang.management.*
import org.apache.log4j.*
import org.apache.commons.io.*

public class WebServer implements Runnable {
  private final static Log log = Log.getLogger(this)
  private static Logger accessLog = Logger.getLogger("ToxicWebAccess")

  private def defaultType
  private def content
  private JobManager jobManager
  private boolean gzipEnabled
  protected Service sparkService

  public WebServer(JobManager jobManager, serverPort) {
    this.jobManager = jobManager
    this.sparkService = Service.ignite()
      .port(serverPort)
      .webSocketIdleTimeoutMillis(300 * 1000)

    Spark.exception(Exception.class, { exception, request, response -> throw(e) });
  }
  
  public void setGzipEnabled(boolean enabled) {
    gzipEnabled = enabled
  }
  
  public stop() {
    this.sparkService.stop()
  }
  
  public String getServerUrl() {
    def props = jobManager?.currentProperties()
    def url = props?."web.serverUrl" ?: null
    if (url?.size() > 1) {
      url = url.trim()
      if (url.endsWith("/")) {
        url = url[0..-2]
      }
    }
    return url
  }

  protected def forbidIfNoResponse(Response resp, Closure c) {
    def result = c()
    if (result || result == 0) {
      return makeResponse(result)
    } 
    resp.status(403)
    return "Forbidden"
  }

  public Object makeResponse(Object obj, boolean json=true) {
    if (json) {
      return new JsonBuilder(obj == null ? [:] : obj)
    }
    
    return obj
  }

  public void run() {

    sparkService.staticFiles.location("web")

    addWebSocketRoute("/ws/echo", new EchoHandler().start())

    addWebSocketRoute("/ws/environment", new EnvironmentHandler([jobManager: jobManager, eventCollector: new EventCollector(10)]).start())

    addWebSocketRoute("/ws/projects", new ProjectHandler())

    addRoute("/") { req, resp ->
      resp.redirect("/ui/index")
    }

    addRoute("/ui/index") { req, resp ->
      def data = this.class.getClassLoader().getResourceAsStream("web/views/index")

      IOUtils.toString(data)
    }

    addRoute("/api/projects") { req, resp ->
      def since = null
      def sinceStr = req.queryParams("since")
      if (sinceStr && sinceStr.isNumber()) {
        since = new Date(sinceStr.toLong())
      }

      def result = [:]
      result.since = System.currentTimeMillis() // capture time first in case projects change while collecting them
      result.projects = jobManager.browseProjects(since)
      
      makeResponse(result)
    }

    addRoute("/api/echo") { req, resp ->
      def results = Auth.identify(jobManager.jobProperties(), req.raw(), resp.raw(), req.queryParams('token')?.trim())
      results.status = 'ok'
      makeResponse(results)
    }

    addRoute("/api/authrequest") { req, resp ->
      forbidIfNoResponse(resp) {
        Auth.request(jobManager.jobProperties(), req.raw(), resp.raw(), req.queryParams('auth')?.trim(), req.queryParams('type'), req.queryParams('loc'))
      }
    }

    addRoute("/api/authvalidate") { req, resp ->
      forbidIfNoResponse(resp) {
        Auth.validate(jobManager.jobProperties(), req.raw(), resp.raw(), req.queryParams('token')?.trim())
      }
    }

    addRoute("/api/jobs") { req, resp ->
      makeResponse(jobManager.browseJobs())
    }

    addRoute("/api/jobs/:project") { req, resp ->
      makeResponse(jobManager.browseJobs(req.params(':project')))
    }

    addRoute("/api/projects") { req, resp ->
      def since = null
      def sinceStr = req.queryParams("since")
      if (sinceStr && sinceStr.isNumber()) {
        since = new Date(sinceStr.toLong())
      }

      def result = [:]
      result.since = System.currentTimeMillis() // capture time first in case projects change while collecting them
      result.projects = jobManager.browseProjects(since)

      makeResponse(result)
    }

    addRoute("/api/project/:id/latest") { req, resp ->
      makeResponse(jobManager.findLatestJob(req.params(':id'), JobStatus.COMPLETED))
    }

    addRoute("/api/project/:id/latest/:status", false) { req, resp ->
      def status = JobStatus.values().find { s -> s.toString().toLowerCase() == req.params(':status') }
      makeResponse(jobManager.findLatestJob(req.params(':id'), status))
    }

    addRoute("/api/project/:id/latest/artifact/:filename") { req, resp ->
      resp.type(ContentType.getMimeType(req.params(':filename')))
      def filename = req.params(':filename')
      def latestJobMap = jobManager.findLatestJob(req.params(':id'), JobStatus.COMPLETED)
      makeResponse(jobManager.findJob(latestJobMap['id'])?.fetchArtifact(filename), false)
    }

    addRoute("/api/project/:id/pause") { req, resp ->
      makeResponse(PauseManager.instance.pauseProject(jobManager, req.params(':id')))
    }

    addRoute("/api/project/:id/unpause") { req, resp ->
      makeResponse(PauseManager.instance.unpauseProject(jobManager, req.params(':id')))
    }

    addRoute("/api/job/:id/docs") { req, resp -> 
      def job = jobManager.findJob(req.params(':id'))
      def docs = [
        readme: job.getReadMeFile().exists() ? job.getReadMeFile().text : "" 
      ]

      makeResponse(docs)
    }

    addRoute("/api/job/:id/images") { req, resp ->
      makeResponse(jobManager.findJob(req.params(':id')).getImageRepo()?.images)
    }

    addRoute("/api/job/:id") { req, resp ->
      makeResponse(jobManager.findJob(req.params(':id'))?.toSimple())
    }

    addRoute("/api/job/:id/action/:action") { req, resp ->
      forbidIfNoResponse(resp) {
        def data = Auth.identify(jobManager.jobProperties(), req.raw(), resp.raw(), req.queryParams('token')?.trim())
        def id = req.params(':id')
        def action = req.params(':action')
        def result = jobManager.findJob(id)?.performAction(action, data.name)
        log.info("Action completed; job='${id}'; action='${action}'; auth='${data.name}'; result='${result}'")
        return result
      }
    }

    addPostRoute("/api/job/:id/publish/results") { req, resp ->
      Job job = jobManager.findJob(req.params(':id'))
      log.debug("Appending task results to job; job=${job.id}; taskResults=${req.body()}")
      def taskResults = new JsonSlurper().parseText(req.body())
      taskResults.each {
        job.addRemoteResult(new TaskResult(it))
      }
      
      makeResponse([:])
    }

    addPostRoute("/api/job/:id/publish/artifact/:resource", false) { req, resp ->
      Job job = jobManager.findJob(req.params(':id'))
      IOUtils.copy(req.raw().getInputStream(), new FileOutputStream(new File(job.artifactsDir, req.params(':resource'))))
      
      makeResponse([:]).toString()
    }

    addRoute("/api/job/:id/halt") { req, resp ->
      jobManager.findJob(req.params(':id'))?.halt()
    }

    addRoute("/api/job/:id/start") { req, resp ->
      def results = [:]
      results.jobId = jobManager.runJob(jobManager.findJob(req.params(':id')))
      makeResponse(results)
    }

    addRoute("/api/job/:id/suites/:bookmark") { req, resp ->
      long fromMillis = req.params(':bookmark').toLong()
      long toMillis = System.currentTimeMillis()
      def job = jobManager.findJob(req.params(':id'))

      def filterParam = req.queryParams("statusFilter")
      def targetSuccessValue = !filterParam || filterParam == "both" ? null : filterParam == "success" ? true : false

      def suites = job?.toSuiteBreakdown(fromMillis, Long.MAX_VALUE)
      if(targetSuccessValue != null) {
        log.debug("Returning only suites with success=${targetSuccessValue}")
        suites = suites.findAll { it.success == targetSuccessValue }
      }

      def map = [bookmark: toMillis, suites: suites, job: job?.toSimple()]
      
      makeResponse(map)
    }

    addRoute("/api/job/:id/log/:filename") { req, resp ->
      resp.type(ContentType.getMimeType(req.params(':filename')))
      jobManager.findJob(req.params(':id'))?.fetchLog()
    }

    addRoute("/api/job/:id/artifacts") { req, resp ->
      def job = jobManager.findJob(req.params(':id'))
      def data = [:]
      if (job) {
        data.job = job.toSimple()
        data.artifacts = job.fetchArtifacts()
      }

      makeResponse(data)
    }

    addRoute("/api/job/:id/artifact/:filename") { req, resp ->
      resp.type(ContentType.getMimeType(req.params(':filename')))
      def filename = req.params(':filename')

      // Note that Spark appears to force URLs to lowercase.  So all artifacts 
      // need to use lowercase chars.
      makeResponse(jobManager.findJob(req.params(':id'))?.fetchArtifact(filename), false)
    }

    addRoute("/api/job/:id/log/:offset/:max") { req, resp ->
      def soffset = req.params(':offset')
      def smax = req.params(':max')
      log.info("Getting ${smax} max bytes from offset ${soffset}")
      
      def data = jobManager.findJob(req.params(':id'))?.fetchLog(soffset?.isNumber() ? soffset.toInteger() : 0, smax?.isNumber() ? smax.toInteger(): 0 )

     makeResponse(data)
    }

    addRoute("/api/job/:id/logHead/:lines") { req, resp ->
      def slines = req.params(':lines')
      def numLines = slines ? "${slines}".toInteger() : 500
      log.debug("Getting ${numLines} lines from log head")
      def job = jobManager.findJob(req.params(':id'))
      def data = [:]
      if (job) {
        data.job = job.toSimple()
        data.log = job.logHead(numLines)
      }

      makeResponse(data)
    }

    addRoute("/api/job/:id/logTail/:lines") { req, resp ->
      def slines = req.params(':lines')
      def numLines = slines ? "${slines}".toInteger()+1 : 500
      log.debug("Getting ${numLines} lines from log tail")
      def job = jobManager.findJob(req.params(':id'))
      def data = [:]
      if (job) {
        data.job = job.toSimple()
        data.log = job.logTail(numLines)
      }

      makeResponse(data)
    }

    addRoute("/api/job/:id/suite/:suiteId") { req, resp ->
      def jobId = req.params(':id')
      def suiteId = req.params(':suiteId')

      makeResponse(jobManager.findJob(jobId)?.toTaskBreakdown(URLDecoder.decode(suiteId, "UTF-8")))
    }

    addRoute("/api/job/:id/tasks") { req, resp ->
      def jobId = req.params(':id')
      def taskHierarchy = [:]
      if(jobId) {
        def job = jobManager.findJob(jobId)
        taskHierarchy = job.getTaskResultsHierarchy()
      }

      makeResponse(taskHierarchy)
    }

    addRoute("/api/job/:id/ack/:user") { req, resp ->
      def result = false
      def job = jobManager.findJob(req.params(':id'))
      def user = UserManager.instance.getById(req.params(':user'))
      
      if (job && user) {
        result = AckManager.instance.ack(jobManager, job.id, user.id)
      }

      makeResponse([result:result])
    }

    addRoute("/api/job/:id/resolve/:user") { req, resp ->
      def result = false
      def job = jobManager.findJob(req.params(':id'))
      def user = UserManager.instance.getById(req.params(':user'))
      
      if (job && user) {
        result = AckManager.instance.resolve(jobManager, job.id, user.id)
      }
      
      makeResponse([result:result])
    }

    addRoute("/api/job/:id/changeset/:changeset") { req, resp ->
      def diff = ""
      def job = jobManager.findJob(req.params(':id'))
      if (job) {
        job.attachRepository()
        diff = job.repo.getDiff(req.params(':changeset'))
      }

      makeResponse([details:diff])
    }

    addRoute("/api/user/:id") { req, resp ->
      makeResponse(UserManager.instance.find(req.params(':id')))
    }

    addRoute("/api/user/:id/avatar/:size") { req, resp ->
      def user = UserManager.instance.getById(req.params(':id'))

      if (user) {
        def image = user.profile?."image_${req.params(':size')}"

        if (image) {
          def url = new URL(image)
          def raw = resp.raw()

          resp.type("image/jpeg")
          resp.header("Content-Encoding", "none")

          try {
            raw.outputStream.write(url.bytes)
            raw.outputStream.flush()
            raw.outputStream.close()
          } catch (Exception e) {
            e.printStackTrace()
          }
          
          return raw
        }
      }
    }

    addRoute("/api/about") { req, resp ->
      def map = [:]
      map.jobParams = [:] as TreeMap
      map.sysParams = [:] as TreeMap
      Main.loadProperties(new Job().loadJobDetails()).findAll { k, v -> isPresentableKey(k) }.each { k, v -> 
        map.jobParams.put(k.toString(), v.toString())
      }
      jobManager.currentProperties().findAll { k, v -> isPresentableKey(k) }.each { k, v -> 
        map.sysParams.put(k.toString(), v.toString())
      }
      map.appVersion = Toxic.version
      map.appBuildDate = Toxic.buildDate
      map.appBuildTime = Toxic.buildTime
      map.threadDump = Environment.instance.generateThreadDump()
      map.log = jobManager.getLogContents()

      makeResponse(map)
    }

    addPostRoute("/api/webhook/upsource") { req, resp ->
      def event = new UpsourceDiscussionEvent(req.body()).getMessage()
      def user = SlackBot.instance.findUser(null, event.user, event.email)
      def channels = jobManager.currentProperties().find { k,v -> k == 'job.slack.channels' }?.value

      if (user) {
        SlackBot.instance.sendMessageToUsers(user.name, event.text)
      }
      else if (channels) {
        SlackBot.instance.sendMessageToChannels(channels, event.text)
      }
      else {
        log.warn("No suitable recipient found for Upsource discussion event notification")
      }

      makeResponse([:])
    }

    addRoute("/metrics") { req, resp ->
      resp.type("text")

      def sb = new StringBuilder()
      jobManager.getMetrics().each { k,v -> sb.append("${k} ${v}\n")} 
      makeResponse(sb.toString(), false)
    }
  }

  boolean isPresentableKey(def k) {
    k = k.toString()
    return !k.startsWith("groovyshell") && (!(k in ['job', 'jobManager', 'eventManager']))
  }

  protected void addWebSocketRoute(String route, Object handler) {
    sparkService.webSocket(route, handler)
  }

  protected void addRoute(String route, boolean useGzip = true, Closure c) {
    sparkService.get(route, new ApiRoute(route, c, useGzip))
  }

  protected void addPostRoute(String route, boolean useGzip = true, Closure c) {
    sparkService.post(route, new ApiRoute(route, c, useGzip))
  }
}


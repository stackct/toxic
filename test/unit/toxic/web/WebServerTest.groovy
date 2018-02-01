package toxic.web

import org.apache.log4j.Level
import org.apache.log4j.Logger
import spark.*
import org.junit.*
import java.io.*
import org.apache.commons.io.*
import groovy.mock.interceptor.*
import javax.servlet.*
import javax.servlet.http.*
import toxic.job.*
import toxic.*
import groovy.json.*

public class WebServerTest {

  def props = new ToxicProperties()
  def server
  def routes = [:]
  def gotSince
  def jobs = [:]
  def jobManager = null

  @Before
  public void before() {
    gotSince = null
    jobs["a"] = new Job("a", new File("a"), new File("a"), "a")
    jobs["b"] = new Job("b", new File("b"), new File("b"), "b")
    jobs["c"] = new Job("c", new File("c"), new File("c"), "c")
    jobs.each { id, j -> j.submittedDate = null }

    jobManager = new JobManager("test", "dir") {
      public def browseProjects(Date since) {
        gotSince = since
        return [[project:'p1'],[project:['p2']]]
      }
      public def browseJobs() {
        return jobs.collect { it.toSimple() }
      }
      public def findJob(id) {
        def job = jobs[id] ?: new Job(id:id, jobDir:new File("a"), name:"a")
        job.submittedDate = null
        return job
      }
      public def currentProperties() {
        return props
      }
    }
      
    server = new WebServer(jobManager, 8001)

    server.sparkService.metaClass.get  = { String s, Route r -> routes[s] = r } 
    server.sparkService.metaClass.post = { String s, Route r -> routes[s] = r }
  }

  @After
  public void after() {
    server.metaClass = null
    server.sparkService.metaClass = null
    
    try {
      server.stop()
    } finally {
    }
  }

  @Test
  public void should_set_up_routes() {
    server.run()

    assert routes['/']                                            != null
    assert routes['/ui/index']                                    != null
    assert routes['/api/projects']                                != null
    assert routes['/api/project/:id/latest']                      != null
    assert routes['/api/project/:id/latest/:status']              != null
    assert routes['/api/project/:id/latest/artifact/:filename']   != null
    assert routes['/api/project/:id/pause']                       != null
    assert routes['/api/project/:id/unpause']                     != null
    assert routes['/api/echo']                                    != null
    assert routes['/api/authrequest']                             != null
    assert routes['/api/authvalidate']                            != null
    assert routes['/api/jobs']                                    != null
    assert routes['/api/jobs/:project']                           != null
    assert routes['/api/job/:id']                                 != null
    assert routes['/api/job/:id/action/:action']                  != null
    assert routes['/api/job/:id/halt']                            != null
    assert routes['/api/job/:id/start']                           != null
    assert routes['/api/job/:id/suites/:bookmark']                != null
    assert routes['/api/job/:id/log/:filename']                   != null
    assert routes['/api/job/:id/log/:offset/:max']                != null
    assert routes['/api/job/:id/artifacts']                       != null
    assert routes['/api/job/:id/artifact/:filename']              != null
    assert routes['/api/job/:id/suite/:suiteId']                  != null
    assert routes['/api/job/:id/tasks']                           != null
    assert routes['/api/job/:id/publish/results']                 != null
    assert routes['/api/job/:id/publish/artifact/:resource']      != null
    assert routes['/api/job/:id/docs']                            != null
    assert routes['/api/job/:id/images']                          != null
    assert routes['/api/job/:id/ack/:user']                       != null
    assert routes['/api/job/:id/resolve/:user']                   != null
    assert routes['/api/job/:id/changeset/:changeset']            != null
    assert routes['/api/user/:id']                                != null
    assert routes['/api/user/:id/avatar/:size']                   != null
    assert routes['/api/about']                                   != null

    server.sparkService.webSocketHandlers.with { ws ->
      assert ws['/ws/echo'].handler instanceof EchoHandler
      assert ws['/ws/environment'].handler instanceof EnvironmentHandler
      assert ws['/ws/projects'].handler instanceof ProjectHandler
    }
  }
  
  @Test
  public void should_forbid() {
    int setcode
    def response = [status: { code -> setcode=code }] as Response
    def result = server.forbidIfNoResponse(response) {
      return false
    }
    
    assert setcode == 403;
    assert result == "Forbidden"
  }

  @Test
  public void should_not_forbid_on_success_exit_code() {
    def response = [status: { code -> setcode = code }] as Response
    
    boolean forbid = true
    server.metaClass.makeResponse = { def o -> forbid = false }

    def result = server.forbidIfNoResponse(response) {
      return 0
    }

    assert !forbid
  }
  
  @Test
  public void should_make_response() {
    assert server.makeResponse(null).toString() == '{}'
    assert server.makeResponse([x:1, y:"ok"]).toString() == '{"x":1,"y":"ok"}'
    assert server.makeResponse(null, false) == null
    assert server.makeResponse([x:1, y:"ok"], false) == [x:1, y:"ok"]

    // 0 should be converted to string of "0" for action buttons
    assert server.makeResponse(0, true).toString() == "0"
  }

  @Test
  public void should_get_server_url() {
    assert server.serverUrl == null
    props["web.serverUrl"] = "http://server/qs"
    assert server.serverUrl == "http://server/qs"
    props["web.serverUrl"] = "http://server/qs/"
    assert server.serverUrl == "http://server/qs"
  }

  @Test
  void should_check_is_presentable() {
    assert server.isPresentableKey("foo")
    assert !server.isPresentableKey("groovyshell")
    assert !server.isPresentableKey("groovyshell[:]")
    assert !server.isPresentableKey("job")
    assert server.isPresentableKey("job.x")
    assert !server.isPresentableKey("jobManager")
    assert server.isPresentableKey("jobManager.x")
    assert !server.isPresentableKey("eventManager")
    assert server.isPresentableKey("eventManager.x")
  }
  
  @Test
  public void should_accept_published_task_results() {
    def job = new Job(agent: new ToxicAgent())
    boolean calledFindJob = false
    jobManager.metaClass.findJob = { def id ->
      calledFindJob = true
      assert '12345' == id
      job.id = id
      job
    }

    def routes = [:]
    server.sparkService.metaClass.get.post = {String s, Route r -> routes[s] = r }
    server.run()

    def taskResults = []
    taskResults << new TaskResult(id:1, family:'family1').toSimple()
    taskResults << new TaskResult(id:2, family:'family2').toSimple()
    taskResults << new TaskResult(id:3, family:'family3').toSimple()

    String jsonRequest = JsonOutput.toJson(taskResults)
    def request  = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, params:{ String key -> '12345'}, body:{->jsonRequest}] as Request
    def response = [header:[:], type: { String s -> }] as Response
    Logger.getRootLogger().setLevel(Level.DEBUG)
    WebServer.log.track { logger ->
      assert '{}' == routes['/api/job/:id/publish/results'].handle(request, response).toString()
      assert logger.isLogged("Appending task results to job; job=12345; taskResults=${jsonRequest}", Level.DEBUG)
      assert 3 == job.agent.remoteTaskMaster.results.size()
      assert calledFindJob
    }
  }

  @Test
  void should_accept_published_artifact() {
    File tempDir
    try {
      tempDir = File.createTempDir()

      def job = new Job(agent: new ToxicAgent(), artifactsDir:tempDir)
      boolean calledFindJob = false
      jobManager.metaClass.findJob = { def id ->
        calledFindJob = true
        assert '12345' == id
        job.id = id
        job
      }

      boolean artifactCopied = false
      IOUtils.metaClass.'static'.copy = { InputStream input, OutputStream output ->
        artifactCopied = true
        assert input
        assert output
      }

      server.run()

      def request = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, params: { String key ->
        if (':id' == key) {
          return '12345'
        } else if (':resource' == key) {
          return 'results.tar.gz'
        }
        return null
      }, raw           : { -> [getInputStream: { [:] as ServletInputStream }] as HttpServletRequest }] as Request

      def response = [header: [:], type: { String s -> }, raw: { -> [getStatus: { -> 0 }] as HttpServletResponse
      }] as Response

      assert '{}' == routes['/api/job/:id/publish/artifact/:resource'].handle(request, response)
      assert calledFindJob
      assert artifactCopied
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_fetch_about_details() {
    def loadedProps
    Main.metaClass.'static'.loadProperties = { String[] args -> loadedProps = true; return [:] }
    server.run()

    def request = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, params: { String key ->
      return null
    }, raw: { -> [getInputStream:{[:] as ServletInputStream}] as HttpServletRequest }] as Request
    def response = [header: [:], type: { String s -> }, raw: { -> [getStatus: { -> 0 } ] as HttpServletResponse }] as Response
    def result = routes['/api/about'].handle(request, response)
    assert loadedProps
  }

  @Test
  void should_fetch_projects_since() {
    Main.metaClass.'static'.loadProperties = { String[] args -> return [:] }
    server.run()

    def dt = new Date()
    def request = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, queryParams: { String key ->
      assert key == "since"
      return dt.time.toString()
    }, raw: { -> [getInputStream:{[:] as ServletInputStream}] as HttpServletRequest }] as Request
    def response = [header: [:], type: { String s -> }, raw: { -> [getStatus: { -> 0 } ] as HttpServletResponse }] as Response
    def result = routes['/api/projects'].handle(request, response).toString()
    
    assert gotSince == dt
    assert result.contains("projects")
    assert result.contains("since")
  }

  @Test
  void should_fetch_projects_null_since() {
    Main.metaClass.'static'.loadProperties = { String[] args -> return [:] }
    server.run()

    def request = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, queryParams: { String key ->
      assert key == "since"
      return "null"
    }, raw: { -> [getInputStream:{[:] as ServletInputStream}] as HttpServletRequest }] as Request
    def response = [header: [:], type: { String s -> }, raw: { -> [getStatus: { -> 0 } ] as HttpServletResponse }] as Response
    def result = routes['/api/projects'].handle(request, response).toString()
    
    assert gotSince == null
    assert result.contains("projects")
    assert result.contains("since")
  }

  @Test
  void should_fetch_latest_artifact() {
    def jobId
    def jobStatus
    def fetchArtifacts = ['somefile.md':'#MARKDOWN']
    jobManager.metaClass.findLatestJob = { String id, JobStatus status ->
      jobId = id
      jobStatus = status
      ['id': id]
    }
    jobManager.metaClass.findJob = { String id ->
      [fetchArtifact:{ filename -> fetchArtifacts[filename] }]
    }

    server.run()

    String responseType = null
    def requestParams = [':id':'project-ci', ':filename':'somefile.md']
    def paramsClosure = { key ->
      requestParams[key]
    }
    def request = [ip: { -> "ip" }, url: { -> "url" }, userAgent: { -> "userAgent" }, params:paramsClosure, queryParams:[:], raw: { -> [getInputStream:{[:] as ServletInputStream}] as HttpServletRequest }] as Request
    def response = [header: [:], type: { String s -> responseType = s}, raw: { -> [getStatus: { -> 0 } ] as HttpServletResponse }] as Response
    def result = routes['/api/project/:id/latest/artifact/:filename'].handle(request, response).toString()
    assert 'text/markdown' == responseType
    assert 'project-ci' == jobId
    assert JobStatus.COMPLETED == jobStatus
    assert '#MARKDOWN' == result
  }
}

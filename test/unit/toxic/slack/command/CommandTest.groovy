package toxic.slack.command

import org.junit.*

import toxic.*
import toxic.job.*
import toxic.web.*
import toxic.slack.*

public class CommandTest {
  def sh
  def jm
  def job
  def projects
  def jobs
  def urlText
  def url
  def bot
  def ran
  def result
  def currentPropsCalled
  def jobPropsCalled
  def sent = []
  def customProps = [:]
  def processingJobs = []
  def running = false

  @Before
  void before() {
    reset()

    currentPropsCalled = 0
    jobPropsCalled = 0
    
    customProps.put("mail.host", "host1")
    customProps.put("path", "conf/templates")
    customProps.put("slack.maxCachedMessagesPerUser", "250")
    customProps.put("slack.triggeredEmailRecentRegex", "OPENED - Incident #([0-9]+) for \\((.+)\\): (.*)\$")
    customProps.put("slack.triggeredEmailPatternRegex", "(vtq|Vtq|VTQ) #incident([0-9]+)\$")
    customProps.put("slack.triggeredEmailPatternSearchCriteriaGroup", "2")
    customProps.put("slack.triggeredEmailTemplate", "triggered-email")
    customProps.put("slack.triggeredEmailRecipient", "help@mycompany.invalid")
    customProps.put("slack.triggeredEmailRecentRegexBodyGroup", "3")
    customProps.put("slack.triggeredEmailRecentRegexSubjectGroup", "2")
    customProps.put("slack.triggeredEmailBodyMaxLength", "15")
    customProps.put("slack.triggeredEmailSubjectMaxLength", "10")

    jm = new JobManager("url", "jobdir") {
      def findJob(id) {
        if(jobs && jobs[id]) {
          return jobs[id]
        }
        return job
      }
      def browseJobs() { return jobs.values() }
      String getConfigDir() { return "/tmp/toxic-config" }
      def browseProjects() { return projects }
      def browseJobs(String project) { browseJobs().findAll { j -> j.project == project }.collect { it.toSimple()} }
      String runJob(Job job) { ran = job; return result }
      def currentProperties() { currentPropsCalled++; super.currentProperties() }
      def jobProperties() { jobPropsCalled++; super.jobProperties() + customProps }
      List fetchProcessingJobs() { return processingJobs }
      boolean isManagerRunning() { return running }
    }
    
    def ws = new WebServer(jm, 8001) {
      String getServerUrl() {
        return "http://foo:8001"
      }
    }

    ToxicServer.setServer(new ToxicServer([:]))
    ToxicServer.server.services = [ jm, ws ]

    bot = new SlackBot()
    bot.rtm = [self:[id:'bot1'], channels:[], groups:[], users:[]]
    sh = new ToxicSlackHandler(jm, ws)
    bot.handler = sh
    URL.metaClass.getText = { -> url = delegate; "urlText" }
    UrlTokenizer.instance.metaClass = null
  }
  
  @After
  void after() {
    reset()
    URL.metaClass = null
    ToxicServer.reset()
    UrlTokenizer.instance.metaClass = null
    SlackBot.metaClass = null
  }
 
  @Test
  public void noop() {

  }

  void reset() {
    // sent.clear()
    // SMTPTransport.metaClass = null
  }  
}
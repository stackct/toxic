
package toxic

import toxic.job.JobManager
import toxic.web.*
import toxic.slack.*
import toxic.user.*

import org.apache.log4j.*
import org.apache.log4j.xml.*

import java.util.concurrent.*

import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.joda.time.Duration;

public class ToxicServer {
  private static Logger log = Logger.getLogger(ToxicServer.class)

  private static ToxicServer server

  static {
    reset()
  }

  public static void reset() {
    server = null
  }
  
  public static void main(String[] args) {

    def cli = new CliBuilder(usage: 'toxic-ui <options>')
    cli.with {
      h longOpt: 'help', 'Show usage information'
      j longOpt: 'jobDir', args: 1, argName: 'job-directory', required: false, 'Local directory where jobs reside; default $TOXIC_HOME/jobs'
      w longOpt: 'webPort', args: 1, argName: 'web-server-port', required: false, 'Web server TCP port; default=8001'
      p longOpt: 'propURL', args: 1, argName: 'properties-file-url', required: false, 'URL of an optional properties file for various Toxic and Job defaults'
      s longOpt: 'secureProps', args: 1, argName: 'secure-properties-file', required: false, 'Local file containing a set of secure properties, used for URL logins, etc.'
    }
    
    def options = cli.parse(args)
    if (!options || options.h) {
      cli.usage()
      System.exit 1
    }

    setServer(new ToxicServer(options))
    server.initServices()
    server.run()
  }

  private static void setServer(ToxicServer val) {
    server = val
  }

  public static ToxicServer getServer() {
    server
  }

  def options
  def services = []
  def startupTime

  public ToxicServer(options) {
    this.options = options
  }

  public String getUpTime() {
    def time = startupTime ? new java.util.Date().time - startupTime : 0
    def duration = new Duration(time)

    def formatter = new PeriodFormatterBuilder()
     .appendDays()
     .appendSuffix("d")
     .appendHours()
     .appendSuffix("h")
     .appendMinutes()
     .appendSuffix("m")
     .appendSeconds()
     .appendSuffix("s")
     .toFormatter()
  
    formatter.print(duration.toPeriod())
  }

  protected void initServices() {
    def jobManager = new JobManager(options.p ?: null, options.j ?: env('TOXIC_HOME') + "/jobs")
    jobManager.loadSecureProperties(options.s ?: null)

    def webServer = new WebServer(jobManager, options.w ? options.w.toInteger(): 8001)
    webServer.gzipEnabled = true

    def slackBot = SlackBot.instance
    slackBot.handler = new ToxicSlackHandler(jobManager, webServer)

    services << { -> Thread.startDaemon("web-srv") { webServer.run() } }
    services << { -> Thread.start("job-mgr") { jobManager.run(); webServer.stop() } }
    services << { -> Thread.startDaemon("slackbot") { slackBot.run() } }
    services << { -> UserManager.instance.init(jobManager.configDir) }
  }

  protected void run() {
    log.info("Starting Toxic Server +++ options=${options};")

    startupTime = new Date().time
    services.each { it.call() }
  }

  private def env(String key) {
    System.getenv()[key]
  }
}



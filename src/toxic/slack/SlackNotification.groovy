package toxic.slack

import org.apache.log4j.*
import javax.mail.*
import javax.mail.internet.*
import javax.activation.*
import groovy.xml.*
import toxic.job.*

public class SlackNotification implements JobNotification {
  private static Logger log = Logger.getLogger(this)

  // The maximum number of failures to list
  private static final int MAX_FAILURES = 5;

  protected Job job
  protected Map jobSimple

  public SlackNotification() {}
    
  public boolean execute(Job job) {
    this.job = job
    this.jobSimple = job.toSimple()
    
    if (shouldNotify()) {
      def channels = job.properties["job.slack.channels"]
      if (channels) {
        SlackBot.instance.sendMessageToChannels(channels, outcome)
      }
      def users = job.properties["job.slack.users"]
      if (users) {
        SlackBot.instance.sendMessageToUsers(users, outcome)
      }
    }
  }

  String getOutcome() {
    def outcome = jobSimple.failed ? localize("FAILED") : localize("SUCCESS")
    def count = jobSimple.failed ? this.jobSimple.failed : this.jobSimple.suites
    def out = "${job.id}: *${outcome} [${count}]* ${linkJob(job.id)}"
    if (job.failed) {
      out += blameList(job)
      out += failureList(job, 5)
    }
    return out
  }

  def blameList(job) {
    def out = ""
    if(job.failed) {
      def blameList = []
      job.commits?.each {
        def userId = SlackBot.instance.findUser(it.user, it.name, it.email)?.id
        if (!(userId in blameList)) {
          out += "\n> Blame " + (userId ? "<@${userId}>" : it.name)
          blameList << userId
        }
      }
    }
    out
  }

  def failureList(Job job, max) {
    def failedSuites = job.toSuiteBreakdown(0, Long.MAX_VALUE).findAll { !it.success }
    def out = failedSuites.collect { "\n> Failed: ${it.suite}" }.take(max).join("")
    if(failedSuites.size() > max) {
      out += "\n> ... and ${failedSuites.size() - max} more"
    }
    out
  }
  
  def linkJob(def job) {
    if (SlackBot.instance.handler) return "(${SlackBot.instance.handler.linkJob(job)})"
    return ""
  }
  
  boolean shouldNotify() {
    if (!job?.properties) return false
    
    def postOnSuccess = !"false".equalsIgnoreCase(job.properties["job.slack.onsuccess"].toString())
    def postOnFailure = !"false".equalsIgnoreCase(job.properties["job.slack.onfailure"].toString())
    def postOnFirstSuccess = !"false".equalsIgnoreCase(job.properties["job.slack.onfirstsuccess"].toString())
    
    return (!SlackBot.instance.isSilenced() &&
            (job.properties["job.slack.channels"] || job.properties["job.slack.users"]) &&
           ((postOnSuccess && !this.jobSimple?.failed) ||
            (postOnFailure && this.jobSimple?.failed) ||
            (postOnFirstSuccess && !this.jobSimple?.failed && this.jobSimple?.prevFailed)))
  }
  
  String localize(String key) {
    return key
  }
}
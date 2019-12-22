package toxic.slack

import groovy.json.*
import javax.servlet.http.*
import org.apache.log4j.*
import toxic.*
import toxic.util.Table
import toxic.job.*
import toxic.user.*
import toxic.web.*
import com.google.common.reflect.ClassPath

import toxic.slack.command.*

public class ToxicSlackHandler implements SlackHandler {
  private static Logger log = Logger.getLogger(this)

  private jobManager
  private webServer
  private recentMessagesByPoster = [:]

  public ToxicSlackHandler(JobManager jobManager, WebServer webServer) {
    if (!jobManager) throw new IllegalArgumentException("jobManager must not be null")

    this.jobManager = jobManager
    this.webServer = webServer
  }

  public Object config(Object key) {
    def props = jobManager.jobProperties()
    if (props) {
      return props[key] ?: null
    }
    return null
  }

  public String getCommandPrefix() {
    config("job.slack.commandPrefix") ?: "."
  }

  public String checkForProhibitedMessage(bot, msg) {
    def sensitive = msg.text ==~ /[^0-9]*[1-9][0-9]{5}[0-9]{4,9}[0-9]{4}[^0-9]*/
    if (sensitive) {
      def url = msg.text ==~ /.*https?:\/\/.*/
      if (!url) {
        return "CAUTION: Detected potentially sensitive data."
      }
    }
    return null
  }

  public void checkForLongUrl(bot, msg) {
    def match = msg.text =~ ".*[<]?(http[s]?://[^ ]+)[>]?"

    if (match.matches()) {
      if (match[0][1].size() > 100) {
        bot.sendMessage(msg.channel, "NOTE: Detected ugly URL. Try .shorten <url>")
      }
    }
  }

  def fetchUser(msg) {
    return msg.user ?: msg.bot_id
  }

  def fetchText(msg) {
    return msg.text ?: (msg.attachments ? msg.attachments[0].text : null)
  }

  public void updateRecentMessages(bot, msg) {
    def user = fetchUser(msg)
    log.debug("Adding message to recent message list; user=${user}; msg: ${msg}")
    if (!user) return
    def msgs = recentMessagesByPoster[user]
    if (!msgs) {
      msgs = []
      recentMessagesByPoster[user] = msgs
    }
    int max = config("slack.maxCachedMessagesPerUser")?.toInteger() ?: 0
    if (msgs.size() > max) {
      msgs.drop(1)
    }
    msgs << fetchText(msg)
  }

  /**
   * Returns a list of recent, in order, messages for the given user. If the pattern is provided
   * then only those messages matching the pattern regex will be returned. Further, if a search criteria
   * is specified then only those patterned-matching messages that contain a regex group that matches
   * the given search criteria will be returned.
   *
   * The returned list contains 0 or more messages, where each message is actually an array of Strings
   * where the first position is the full message and subsequent positions in the array represent
   * the value from the pattern's optional groups.
   */
  List findRecentMsgDetails(recentUserId, pattern = null, searchCriteria = null) {
    def matches = [] as List
    def msgs = recentMessagesByPoster[recentUserId]
    msgs?.each { msg ->
      if (pattern) {
        def match = msg =~ pattern
        if (match.matches() && (!searchCriteria || match[0].find { it == searchCriteria })) {
          matches << match[0]
        }
      } else {
        matches << [msg]
      }
    }
    return matches
  }

  public String checkForRecentPattern(bot, msg) {
    def response

    def pattern = config("slack.triggeredEmailPatternRegex")
    def recipient = config("slack.triggeredEmailRecipient")
    if (pattern && recipient) {
      def text = fetchText(msg)
      def match = text =~ pattern
      log.debug("Checking for recent pattern; match=${match}; msg=${msg}; text=${text}; pattern=${pattern}")
      if (match.matches()) {
        def incident
        if (match[0].size() > 1) {
          incident = fetchMatchPiece(match[0], config("slack.triggeredEmailPatternSearchCriteriaGroup"))
        }
        def details = findRecentMsgDetails(fetchUser(msg), config("slack.triggeredEmailRecentRegex"), incident)
        if (details) {
          def body = fetchMatchPiece(details[0], config("slack.triggeredEmailRecentRegexBodyGroup"), config("slack.triggeredEmailBodyMaxLength"))
          def subject = fetchMatchPiece(details[0], config("slack.triggeredEmailRecentRegexSubjectGroup"), config("slack.triggeredEmailSubjectMaxLength"))
          sendEmail(config("slack.triggeredEmailTemplate"), recipient, subject, body)
          response = "Sent triggered email to ${recipient}"
        } else {
          response = ":warning: No matching message found; unable to trigger email"
        }
      }
    }
    return response
  }

  def fetchMatchPiece(match, group, max = null) {
    def groupIdx = group?.size() > 0 ? group.toInteger() : 0
    def result = match.size() > groupIdx ? match[groupIdx] : match[0]
    if (max?.size() > 0) {
      result = result.take(max.toInteger())
    }
    return result
  }

  def sendEmail(template, recipient, subject, body) {
    def attributes = [body: body]
    def smtp = Notifications.createSmtp(jobManager.jobProperties())

    // Don't use the template subject, use the one we just parsed
    def props = [:]
    props.overrides = props.overrides ?: [:]
    props.overrides.subject = subject
    template.TemplateBuilder.instance.cache.clear()
    template.TemplateBuilder.instance.config.putAll(props)

    smtp.email(template, recipient, attributes);
  }

  public String parseCommandFromEmailMsg(msg) {
    def keys = ['subject','plain_text']
    def text
    if (msg?.file?.mode == "email") {
      def prop = keys.find { msg.file[it]?.trim()?.startsWith(commandPrefix) }
      if (!prop) {
        prop = keys.find { it.trim() }
      }
      text = msg.file[prop]
      if (text.startsWith(commandPrefix)) {
        text = text[(commandPrefix.size())..-1]
      }
    }
    return text
  }

  public String handleCommand(SlackBot bot, def msg) {
    if (!msg || !bot || msg.user == bot.rtm?.self?.id) return null

    updateRecentMessages(bot, msg)

    def result = checkForProhibitedMessage(bot, msg) ?: checkForRecentPattern(bot, msg)
    if (result) return result

    def keyword = "<@${bot?.rtm?.self?.id}>"
    def text
    if (msg.text?.contains(keyword)) {
      text = msg.text.replaceAll(keyword + ":", "").replaceAll(keyword + "-", "").replaceAll(keyword + " -", "").replaceAll(keyword, "").trim()
    } else if (msg.text?.startsWith(commandPrefix) && msg.text.size() > 1) {
      text = msg.text[(commandPrefix.size())..-1].trim()
    } else {
      text = parseCommandFromEmailMsg(msg)
    }

    if (text) {
      def pieces = text?.split(" ")
      def cmd = pieces ? pieces[0].toLowerCase() : null
      def args = pieces?.size() > 1 ? pieces[1..-1] : []
      def commands = CommandFactory.available()

      if (!UserManager.instance.getById(msg.user)) {
        UserManager.instance.add(bot.find(msg.user))
      }

      log.info("Received incoming command message; msg=\"${msg}\"; cmd=\"${cmd}\"")
      if (cmd && ((cmd in ["help", "commands"]) || (cmd[0] in ['?', '-', '/']))) {
        result = "available commands: " + commands.sort().join(", ")
      } else if (commands.contains(cmd)) {
        result = CommandFactory.make(cmd, this).handle(args, bot, msg)
      } else {
        result = fetchExternalUrl(msg)?.text
      }
    }
    archiveToSplunk(bot, msg)
    return result
  }

  def archiveToSplunk(bot, msg) {
    def wsUrl = config("web.serverUrl")
    def host = wsUrl ? new URL(wsUrl).host : "toxic"
    (msg.text =~ /<@.*?>/).each { msg.text = msg.text.replaceAll(it, "@" + bot.findUser(it[2..-2])?.name)}
    (msg.text =~ /<#.*?>/).each { msg.text = msg.text.replaceAll(it, "#" + bot.findChannel(it[2..-2])?.name)}
    msg.channelName = msg.channel ? bot.findChannel(msg.channel)?.name : null
    msg.userName = msg.user ? bot.findUser(msg.user)?.name : null
    def event = msg.sort().collect { k, v -> "${k}=\"${v}\"" }.join("; ")
    return splunkRequest("/services/receivers/simple?host=${host}&source=slack&sourcetype=chat", event) != null
  }

  def splunkRequest(qs, event) {
    def url
    try {
      url = config("splunk.url")
      if (url) {
        url += qs
        def auth = jobManager.lookupSecureValue(url)
        def c = new URL(url).openConnection()
        if (auth) {
          auth = "Basic " + auth.bytes.encodeBase64().toString()
          c.setRequestProperty("Authorization", auth)
        }
        c.setRequestMethod(event ? "POST" : "GET")
        if (event) {
          c.doOutput = true
          c.outputStream.withWriter { it << event }
        }
        def result = c.inputStream?.text
        if (!(c.responseCode in [ HttpServletResponse.SC_OK,
                                  HttpServletResponse.SC_CREATED,
                                  HttpServletResponse.SC_ACCEPTED])) {
          log.error("Failed to post event; url=${url}; responseCode=${c.responseCode}; reason=${result}")
        } else {
          return result
        }
      }
    } catch (Exception e) {
      log.error("Unable to post Splunk URL; url=${url}; reason=${JobManager.findReason(e)}",e)
    }
    return null
  }

  def parseArgValue(args, key, d = null) {
    def val = args.find { it.startsWith(key) }
    if (val?.trim()?.size() > key?.size()) {
      return val[(key.size())..-1]
    }
    return d
  }

  int parseArgIntValue(args, key, int d) {
    int val = d
    String raw = parseArgValue(args, key)
    if (raw) {
      try {
        val = Integer.parseInt(raw)
      } catch (Exception e) {
        log.error("Invalid argument; key=${key}; value=${raw}")
      }
    }
    return val
  }

  def fetchExternalUrl(def msg) {
    try {
      def url = config("job.slack.externalUrl")
      if (url) {
        def qs = ""
        def includeMsg = config("job.slack.externalUrlIncludeMsg")
        if (includeMsg && "true".equalsIgnoreCase(includeMsg)) {
          qs = (url.contains("?") ? "&" : "?") + "c=${URLEncoder.encode(msg.channel,"UTF-8")}&u=${URLEncoder.encode(msg.user,"UTF-8")}&t=${URLEncoder.encode(msg.text,"UTF-8")}"
        }
        def u = new URL(url + qs)
        return u
      }
    } catch (Exception e) {
      log.warn("Unable to reach external URL; reason=${JobManager.findReason(e)}")
    }
    return null
  }

  def linkJob(def job) {
    return "${webServer.serverUrl}/ui/index#/job/${job}"
  }

  def removeUserAlert(str) {
    return str.toString().replaceAll("@([a-z])([a-z0-9_]+[^a-z0-9_]?)") { all, firstChar, remainder ->
        "${firstChar}.${remainder}"
      }
  }
}

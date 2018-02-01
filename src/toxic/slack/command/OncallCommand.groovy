package toxic.slack.command

import toxic.ToxicServer
import org.apache.log4j.*
import groovy.json.*
import javax.servlet.http.*

class OncallCommand extends BaseCommand {

  public OncallCommand(def handler) { 
    super(handler) 
  }

  private static Logger log = Logger.getLogger(this)

  public String handle(args, bot, msg) {
    if (args && args[0] == "help") return """oncall [team:current-channel]
Lists personnel currently on call for the given team."""

   def channelName = msg.channel ? bot.findChannel(msg.channel)?.name : null
   channelName = channelName ?: msg.channel
   def channelDelim = channelName?.indexOf("_")
    String group = channelDelim > 0 ? channelName.substring(channelDelim + 1) : channelName
    if (args) group = args[0]

    def json = victoropsRequest(jobManager, "team/${group}/oncall/schedule?daysForward=1&daysSkip=0&step=0")
    if (json) {
      def results = new JsonSlurper().parseText(json)
      if (results) {
        def oncallPeople = results.schedule.findAll { row -> row.onCall }.collect { map -> map.overrideOnCall ?: map.onCall }.unique()
        def sb = new StringBuilder()
        sb.append("Currently on-call for ${group}: ${oncallPeople}")
        def response = sb.toString()      
        return response
      }
    }
    return "Failed to lookup oncall personnel for ${group}"
  }

  def victoropsRequest(jobManager, qs) {
    def url

    try {
      url = "https://api.victorops.com/api-public/v1/"
      if (url) {
        url += qs
        def c = new URL(url).openConnection()
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("X-VO-Api-Id", jobManager.lookupSecureValue("victorops.id"))
        c.setRequestProperty("X-VO-Api-Key", jobManager.lookupSecureValue("victorops.key"))
        c.setRequestMethod("GET")
        def result = c.inputStream?.text
        if (!(c.responseCode in [ HttpServletResponse.SC_OK,
                                  HttpServletResponse.SC_CREATED, 
                                  HttpServletResponse.SC_ACCEPTED])) {
          log.error("Failed to GET victorops request; url=${url}; responseCode=${c.responseCode}; reason=${result}")
        } else {
          return result
        }
      }
    } catch (Exception e) {
      log.error("Unable to GET victorops URL; url=${url}; reason=${jobManager.findReason(e)}",e)
    }
    return null
  }
}

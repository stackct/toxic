package toxic.slack.command

import org.apache.log4j.*
import toxic.*
import toxic.user.*
import toxic.util.*

class PerformCommand extends BaseCommand {
  private static Logger log = Logger.getLogger(this)

  public PerformCommand(def handler) {
    super(handler)
  }

  public String handle(args, bot, msg) {
    if (!args || args.size() != 2) return "perform <job-id> <action>"
    def requested=args[0]
    def action = args[1]

    def user = UserManager.instance.getById(msg.user)
    def job = jobManager.findJobByFuzzyId(requested)
    if (!job)
      return "invalid job id: ${requested}"

    def result = job?.performAction(action, user.name)
    log.info("Slack Action completed; job='${job.id}'; action='${action}'; auth='${user.name}'; result='${result}'")
    return result
  }
}

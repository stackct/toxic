package toxic.slack.command

import toxic.user.*
import toxic.job.*

class ResolveCommand extends BaseCommand {

  public ResolveCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "resolve <job-id>"

    def job = args[0]
    def username = msg.user
    User u = UserManager.instance.getById(username)

    if (!u) {
      return "User could not be found '${username}'"
    }

    if (AckManager.instance.resolve(jobManager, job, u.id)) {
      return "<@${u.name}> has resolved '${job}'"
    }
    else {
      return "Could not resolve job '${job}'"
    }
  }  
}
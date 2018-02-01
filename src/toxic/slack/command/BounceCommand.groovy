package toxic.slack.command

import toxic.*

class BounceCommand extends BaseCommand {

  public BounceCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    def processingJobs = jobManager.fetchProcessingJobs()
    if (!args || args[0] == "help") {
      return "bounce <asap | now | cancel>"
    }
    def arg = args[0]

    switch (arg) {
      case "cancel": 
        if (jobManager.cancelShutdown()) {
          return "canceled shutdown."
        }
        return "unable to cancel shutdown, the shutdown is already in progress."
      case "asap":
        jobManager.shutdown()
        if (!processingJobs) {
          return "bouncing now since server is idle."
        }
        return "bouncing asap, waiting for ${processingJobs.size()} running job(s) to complete."
      case "now":
        Thread.start { sleep(100); jobManager.shutdown(true) }
        if (processingJobs) {
          return "bouncing now and aborting ${processingJobs.size()} running job(s)."
        }
        return "bouncing now."
    }
    return "¸.·´¯`·.¸boing¸.´¯`·.¸boing¸.·´¯`·.¸boing!"
  }

}

package toxic.slack.command

import toxic.*
import toxic.job.*

class PauseCommand extends BaseCommand {

  public PauseCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "pause <project-name|all>"
    if (args[0] == "all") {
      jobManager.browseJobs().collect { it.project }.unique().each { PauseManager.instance.pauseProject(jobManager, it) }
    } else {
      PauseManager.instance.pauseProject(jobManager, args[0])
    }
    return "paused project: ${args[0]}"
  }  
}


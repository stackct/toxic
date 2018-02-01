package toxic.slack.command

import toxic.job.*

class UnpauseCommand extends BaseCommand {

  public UnpauseCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "unpause <project-name|all>"
    if (args[0] == "all") {
      PauseManager.instance.unpauseProjects(jobManager)
    } else {
      PauseManager.instance.unpauseProject(jobManager, args[0])
    }
    
    return "unpaused project: ${args[0]}"
  }  
}

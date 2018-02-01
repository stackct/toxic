package toxic.slack.command

import toxic.*

class HaltCommand extends BaseCommand {

  public HaltCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) 
      return "halt <job-id>"

    return jobManager.findJob(args[0])?.halt() ? "halting job: ${args[0]} (${linkJob(args[0])})" : "job not running: ${args[0]}"
  }
}

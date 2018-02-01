package toxic.slack.command

import toxic.*
import toxic.util.Table
import toxic.job.*
import toxic.web.*

class RunCommand extends BaseCommand {

  public RunCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "run <job-id>"
    def requested=args[0]

    // Look for an exact job-id match
    def job = jobManager.findJob(requested)

    // Look for a project match
    if(!job) job = jobManager.findJobByProject(requested)

    if (!job) 
      return "invalid job id: ${requested}"
    
    def jobId = jobManager.runJob(job)
    
    if (!jobId) 
      return "job cannot be started at this time"
    
    return "running new job: ${jobId} (${linkJob(jobId)})"
  }
}

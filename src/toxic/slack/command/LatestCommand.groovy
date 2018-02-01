package toxic.slack.command

import toxic.job.*

class LatestCommand extends BaseCommand {

  public LatestCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "latest <project> [status]"

    def project = args[0]
    def status = JobStatus.values().find { s -> s.toString().toLowerCase() == args[1] }
    def job = jobManager.findLatestJob(project, status)
    
    if (!job) 
      return "No ${ args[1] ? args[1] + ' ' : '' }jobs found for '${project}'"

    return "${job.id} (${job.status}) | ${linkJob(job.id)}"
  }  
}

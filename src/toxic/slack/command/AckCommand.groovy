package toxic.slack.command

import toxic.user.*
import toxic.job.*

class AckCommand extends BaseCommand {

  public AckCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (args[0] == "help") return "ack [<job-id> [user]]"

    def job
    if(args?.size()) {
      job = args[0]
    } else {
      job = findLatestFailedJob()?.id
    }

    if(!job) {
      return "No job found to ack"
    }

    def username = msg.user
    User u

    if (args[1]) {
      username = args[1]
      u = UserManager.instance.find(username)
    } else {
      u = UserManager.instance.getById(username)
    }

    if (!u) {
      return "User '${username}' could not be found"
    }

    if (AckManager.instance.ack(jobManager, job, u.id)) {
      return "<@${u.name}> has acked '${job}'"
    }
    else {
      return "Could not ack job '${job}'"
    }
  }

  protected Job findLatestFailedJob() {
    def failedJobs = jobManager.allJobs().findAll { j -> j.currentStatus == JobStatus.COMPLETED  && !j.successful }
    def sorted = failedJobs.sort { it.completedDate }.reverse()
    return sorted ? sorted.head() : null
  }
}
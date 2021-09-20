package toxic.slack.command

import toxic.*
import toxic.job.*
import toxic.util.*

class ListCommand extends BaseCommand {

  public ListCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    def projects = jobManager.browseProjects()
    if (!projects) return "no projects available"
    def sb = new StringBuffer()

    use (Table) {
      sb.append(projects.collect { j -> 
        [job: j.id, paused: PauseManager.instance.isProjectPaused(j.project) ?: '', status: j.status, failed: j.failed ?: '', blame: blameList(j)]
      }.toTable())
    }
    return "```${sb}```".toString()
  }

  String blameList(def j) {
    return j?.failed ? j.commits?.collect { c -> c.user }?.unique()?.sort()?.join(", ") : null
  }
}

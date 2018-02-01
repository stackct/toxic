package toxic.slack.command

import toxic.*
import toxic.util.*


public class DescribeCommand extends BaseCommand {

  public DescribeCommand(def handler) { 
    super(handler) 
  }

  String handle(args, bot, msg) {
    int maxFailures = 5
    if (!args) return "describe <job-id> <max-failures:${maxFailures}>"

    def jobId = args[0].tokenize(" :").first()
    def job = jobManager.findJob(jobId)
    if (!job) return "invalid job id: ${jobId}"
    if (args.size() > 1) try { maxFailures = args[1].toInteger() } catch (Exception e) {}

    def sb = new StringBuilder()
    sb.append("```")

    def map = [:]
    map["Job"]       = "${job.id} (${linkJob(job.id)})"
    map["Status"]    = job.currentStatus
    map["Started"]   = job.startedDate
    map["Completed"] = job.completedDate
    map["Suites"]    = job.suites
    map["Failed"]    = job.failed

    if (job.startedDate && job.completedDate) {
      map["Duration (ms)"] = job.completedDate?.time - job.startedDate?.time
    }

    use (Table) {
     sb.append(map.toTable())
    }

    if (job.commits?.size() > 0) {
      sb.append("\n")

      use (Table) {
        sb.append(job.commits.toTable())
      }
    }

    def suites = job.toSuiteBreakdown(0, Long.MAX_VALUE)
      .findAll { !it.success }
      .take(maxFailures)
      .collect { suite -> ['suite': suite.suite, 'tasks': suite.tasks, 'duration (ms)': suite.duration] }

    if (suites.size() > 0) {
      sb.append("\n")

      use (Table) {
        sb.append(suites.toTable())
      }
    }

    sb.append("```")

    return sb.toString()
  }
}
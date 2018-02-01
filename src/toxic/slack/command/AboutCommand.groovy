package toxic.slack.command

import toxic.*
import toxic.util.*

class AboutCommand extends BaseCommand {

  public AboutCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    def env = Environment.instance.toSimple()
 
    def heapUsedMb    = ((env.heapUsed as Float)    / (1000 * 1000)).round()
    def heapMaxMb     = ((env.heapMax as Float)     / (1000 * 1000)).round()
    def heapPerc      = ((env.heapUsed as Float)    / (env.heapMax as Float) * 100).round(2)

    def sb = new StringBuilder()
    sb.append("```")

    use (Table) {
      def map = [
        "Version": Toxic.genProductVersionString("Toxic-UI"),
        "Server UpTime": ToxicServer.server.getUpTime(),
        "Server URL": webServer.serverUrl,
        "Operating System": "${env.os} ${env.version} (${env.arch})",
        "Load": env.load,
        "CPUs": env.procs,
        "Memory (Heap)": "${heapPerc}% (${heapUsedMb} Mb / ${heapMaxMb} Mb)",
        "Shutdown Pending": "${jobManager.isShuttingDown()}",
        "Running Jobs": "${jobManager.runningJobs?.size()}"
      ]
      sb.append(map.toTable())
    }

    sb.append("```")
 
    return sb.toString()
  }
}
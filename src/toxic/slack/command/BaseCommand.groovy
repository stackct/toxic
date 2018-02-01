package toxic.slack.command

import toxic.*
import toxic.job.*
import toxic.web.*

abstract class BaseCommand implements Command {
  
  def handler

  public BaseCommand(def handler) {
    this.handler = handler
  }

  protected getJobManager() {
    return this.handler.jobManager
  }

  protected getWebServer() {
    return this.handler.webServer
  }

  def linkJob(def job) {
    return "${handler.webServer.serverUrl}/ui/index#/job/${job}"
  }
}

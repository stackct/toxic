package toxic.slack.command

import org.apache.log4j.*

class LogCommand extends BaseCommand {

  public LogCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (!args) return "log <trace|debug|info|warn|error>"

    Logger.getRootLogger().setLevel(Level.toLevel(args[0], Level.INFO))
    "Log level is now: ${Logger.getRootLogger().getLevel()}"
  }
}

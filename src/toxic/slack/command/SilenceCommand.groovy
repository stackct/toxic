package toxic.slack.command

class SilenceCommand extends BaseCommand {

  public SilenceCommand(def handler) { 
    super(handler) 
  }

  public String handle(args, bot, msg) {
    if (args && args[0] == "help") return """silence [mins:15]
Silences the bot on Slack for the given number of minutes."""

    long mins = 15
    int argIdx = 0
    if (argIdx < args?.size()) mins = new Long(args[argIdx++])
    
    long endTime = mins > 0 ? System.currentTimeMillis() + (mins * 60 * 1000) : 0
    bot.silenceUntil(endTime)

    if (endTime) {
      return "Notifications silenced until " + new Date(endTime)
    }
    
    return "Notifications unsilenced"
  }  
}

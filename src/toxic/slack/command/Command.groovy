package toxic.slack.command

interface Command { 
  public String handle(args, bot, msg);
}
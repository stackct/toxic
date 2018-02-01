package toxic.slack.command

class InvalidCommand extends Exception { 
  public InvalidCommand(String msg) {
    super(msg)
  }
}

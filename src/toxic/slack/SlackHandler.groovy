package toxic.slack

public interface SlackHandler {
  public String handleCommand(SlackBot bot, def msg)
  public Object config(Object key)
}
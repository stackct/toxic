package toxic.slack.command

import org.junit.*

import toxic.*

public class LogCommandTest extends CommandTest {

  @Test
  void should_change_log_level() {
    assert sh.handleCommand(bot, [text:".log boo"]) == "Log level is now: INFO"
    assert sh.handleCommand(bot, [text:".log error"]) == "Log level is now: ERROR"
    assert sh.handleCommand(bot, [text:".log warn"]) == "Log level is now: WARN"
    assert sh.handleCommand(bot, [text:".log info"]) == "Log level is now: INFO"
    assert sh.handleCommand(bot, [text:".log debug"]) == "Log level is now: DEBUG"
    assert sh.handleCommand(bot, [text:".log trace"]) == "Log level is now: TRACE"
  }
}

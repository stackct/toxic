package toxic.slack.command

import org.junit.*

import toxic.*
import toxic.slack.*

public class SilenceCommandTest extends CommandTest {

  @Test
  void should_handle_silence() {
    assert sh.handleCommand(bot, [text: ".silence help"]).contains("Silences the bot on Slack for the given number of minutes.")
    assert !bot.isSilenced()

    assert sh.handleCommand(bot, [text: ".silence"]).startsWith("Notifications silenced until ")
    long start = System.currentTimeMillis()
    assert bot.isSilenced()
    assert bot.silenceEndTime > start && SlackBot.instance.silenceEndTime <= (start + (15*60*1000))

    assert sh.handleCommand(bot, [text: ".silence 1000"]).startsWith("Notifications silenced until ")
    start = System.currentTimeMillis()
    assert bot.isSilenced()
    assert bot.silenceEndTime > (start + (999*60*1000)) && SlackBot.instance.silenceEndTime <= (start + (1000*60*1000))

    assert sh.handleCommand(bot, [text: ".silence 0"]).startsWith("Notifications unsilenced")
    assert !bot.isSilenced()
  }
  
}
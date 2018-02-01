package toxic.slack.command

import org.junit.*

import toxic.*
import toxic.job.*

public class PauseCommandTest extends CommandTest {

  @Test
  void should_pause_unpause() {
    def j = new Job()
    j.id = "some.job-1234"
    def k = new Job()
    k.id = "another.job-544"

    jobs = [(j.id):j,(k.id):k]

    assert !PauseManager.instance.isProjectPaused("some")
    assert sh.handleCommand(bot, [text:".pause some"]) == "paused project: some"
    assert PauseManager.instance.isProjectPaused("some")

    assert sh.handleCommand(bot, [text:".unpause some"]) == "unpaused project: some"
    assert !PauseManager.instance.isProjectPaused("some")

    assert sh.handleCommand(bot, [text:".pause all"]) == "paused project: all"
    assert PauseManager.instance.isProjectPaused("some")
    assert PauseManager.instance.isProjectPaused("another")

    assert sh.handleCommand(bot, [text:".unpause all"]) == "unpaused project: all"
    assert !PauseManager.instance.isProjectPaused("some")
    assert !PauseManager.instance.isProjectPaused("another")
  }

}

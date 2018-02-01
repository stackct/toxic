package toxic.slack.command

import org.junit.*

import toxic.job.*


public class BounceCommandTest extends CommandTest { 

  @Test
  public void should_shutdown_asap_idle() {
    processingJobs = []
    def result = sh.handleCommand(bot, [text:".bounce asap"])
    assert "bouncing now since server is idle." == result
  }

  @Test
  public void should_shutdown_asap_not_idle() {
    processingJobs = ['a']
    def result = sh.handleCommand(bot, [text:".bounce asap"])
    assert "bouncing asap, waiting for 1 running job(s) to complete." == result
  }

  @Test
  public void should_shutdown_now_not_idle() {
    processingJobs = ['a']
    def result = sh.handleCommand(bot, [text:".bounce now"])
    assert "bouncing now and aborting 1 running job(s)." == result
  }

  @Test
  public void should_shutdown_now_idle() {
    processingJobs = []
    def result = sh.handleCommand(bot, [text:".bounce now"])
    assert "bouncing now." == result
  }

  @Test
  public void should_shutdown_cancel_failed() {
    def result = sh.handleCommand(bot, [text:".bounce cancel"])
    assert "unable to cancel shutdown, the shutdown is already in progress." == result
  }

  @Test
  public void should_shutdown_cancel() {
    running = true
    def result = sh.handleCommand(bot, [text:".bounce cancel"])
    assert "canceled shutdown." == result
  }

  @Test
  public void should_show_usage() {
    def result = sh.handleCommand(bot, [text:".bounce"])
    assert "bounce <asap | now | cancel>" == result
  }

  @Test
  public void should_show_help() {
    def result = sh.handleCommand(bot, [text:".bounce help"])
    assert "bounce <asap | now | cancel>" == result
  }

  @Test
  public void should_be_sarcastic() {
    def result = sh.handleCommand(bot, [text:".bounce it"])
    assert result.contains("boing")
  }
}
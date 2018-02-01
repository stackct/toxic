package toxic.slack.command

import org.junit.*

import toxic.job.*


public class DescribeCommandTest extends CommandTest { 

  @Test
  void should_handle_trailing_text_on_describe_job_id() {
    def j = new Job()
    j.id = "some.job-1234"
    jobs = ["some.job-1234": j]

    assert sh.handleCommand(bot, [text: "describe blarf <@bot1>"]) == "invalid job id: blarf"
    assert !sh.handleCommand(bot, [text: "describe some.job-1234 <@bot1>"]).contains("invalid job id")
    assert !sh.handleCommand(bot, [text: "describe some.job-1234: FAILED[1] <@bot1>"]).contains("invalid job id")
    assert sh.handleCommand(bot, [text: "describe some.job-9876: FAILED[1] <@bot1>"]) == "invalid job id: some.job-9876"
  }

}
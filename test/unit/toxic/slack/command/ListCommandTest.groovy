package toxic.slack.command

import org.junit.*

import toxic.*

public class ListCommandTest extends CommandTest {

  @Test
  void should_build_blame_list() {
    def command = new ListCommand()
    def job = [:]
    assert !command.blameList(job)
    
    job.failed = 1
    job.commits = [[user: 'c'],[user:'a'],[user:'b'],[user:'a']]
    assert command.blameList(job) == "a, b, c"
  }
}

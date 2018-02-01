package toxic.slack.command

import org.junit.*

import toxic.*
import toxic.job.*

public class LatestCommandTest extends CommandTest {

  @Test
  void should_return_usage() {
    def latest = sh.handleCommand(bot, [text:".latest"])

    assert latest == "latest <project> [status]"
  }

  @Test
  void should_retrieve_url_for_latest_job() {

    jobs = [:]
    jobs['foo-ci.job-0'] = new Job(id: "foo-ci.job-0", currentStatus: JobStatus.COMPLETED)
    jobs['foo-ci.job-1'] = new Job(id: "foo-ci.job-1", currentStatus: JobStatus.COMPLETED)
    jobs['foo-ci.job-2'] = new Job(id: "foo-ci.job-2", currentStatus: JobStatus.RUNNING)

    assert "foo-ci.job-2 (RUNNING) | http://foo:8001/ui/index#/job/foo-ci.job-2" == sh.handleCommand(bot, [text:".latest foo-ci"])
    assert "foo-ci.job-1 (COMPLETED) | http://foo:8001/ui/index#/job/foo-ci.job-1" == sh.handleCommand(bot, [text:".latest foo-ci completed"])
    assert "No jobs found for 'bar'" == sh.handleCommand(bot, [text: ".latest bar"])
    assert "No running jobs found for 'bar'" == sh.handleCommand(bot, [text: ".latest bar running"])
    assert "No completed jobs found for 'bar'" == sh.handleCommand(bot, [text: ".latest bar completed"])
  }
}

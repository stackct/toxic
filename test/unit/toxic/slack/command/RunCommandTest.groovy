package toxic.slack.command

import org.junit.*

import toxic.job.*


public class RunCommandTest extends CommandTest { 

  @Test
  void should_run_job_by_job_id() {
    job = new Job()
    result = "id1"

    assert sh.handleCommand(bot, [text:".run id1"])  == "running new job: id1 (http://foo:8001/ui/index#/job/id1)"

    assert ran == job

    result = null
    assert sh.handleCommand(bot, [text:".run id1"])  == "job cannot be started at this time"
  }

  @Test
  void should_run_job_by_project_id() {
    projects = [
            ['project': 'testProj', 'id': 'testProj.job-123']
    ]
    def testJob = new Job()
    jobs = ['testProj.job-123': testJob]
    result = 'testProj.job-124'

    assert sh.handleCommand(bot, [text:".run testProj"])  == "running new job: testProj.job-124 (http://foo:8001/ui/index#/job/testProj.job-124)"

    assert ran == testJob

    assert sh.handleCommand(bot, [text:".run foo"])  == "invalid job id: foo"
  }
}

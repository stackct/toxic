package toxic.slack.command

import toxic.slack.SlackHandler
import org.junit.*
import toxic.user.*
import toxic.job.*

@Mixin(UserManagerTestMixin)
public class AckCommandTest extends CommandTest { 
  @Before
  public void before() {
    super.before()
    userManagerSetup()
  }

  @After
  public void after() {
    userManagerTeardown()
    AckManager.instance.metaClass = null
    CommandFactory.metaClass = null
    super.after()
  }

  @Test
  public void should_return_usage_if_help() {
    assert sh.handleCommand(bot, [text: "ack help <@bot1>"]) == "ack [<job-id> [user]]"
  }

  @Test
  public void should_fail_if_user_not_found() {
    assert sh.handleCommand(bot, [text: "ack some.job-1234 <@bot1>", user:'UNKNOWN']) == "User 'UNKNOWN' could not be found"
    assert sh.handleCommand(bot, [text: "ack some.job-1234 OTHER.USER <@bot1>", user:'SELF']) == "User 'OTHER.USER' could not be found"
  }

  @Test
  public void should_ack_failed_job_to_current_user() {
    def acked = [:]

    AckManager.instance.metaClass.ack = { JobManager mgr, String jobId, String user -> acked[jobId] = user; return true }

    assert sh.handleCommand(bot, [text: "ack some.job-1234 <@bot1>", user:user('fred')]) == "<@fred> has acked 'some.job-1234'"
    assert acked['some.job-1234'] == user('fred')
  }

  @Test
  public void should_ack_failed_job_to_provided_user() {
    def acked = [:]

    AckManager.instance.metaClass.ack = { JobManager mgr, String jobId, String user -> 
      assert jobId == 'some.job-1234'
      assert user == 'U1234567'
      acked[jobId] = user
      return true 
    }

    assert sh.handleCommand(bot, [text: "ack some.job-1234 <@bot1>", user:user('fred')]) == "<@fred> has acked 'some.job-1234'"
    assert sh.handleCommand(bot, [text: "ack some.job-1234 fred <@bot1>", user:user('fred')]) == "<@fred> has acked 'some.job-1234'"
    assert acked['some.job-1234'] == user('fred')
  }

  @Test
  public void should_handle_failed_ack() {
    AckManager.instance.metaClass.ack = { JobManager mgr, String jobId, String user -> return false }
    assert sh.handleCommand(bot, [text: "ack some.job-1234 <@bot1>", user:user('fred')]) == "Could not ack job 'some.job-1234'"
  }

  def cmdWithCompletedJobs(completedJobs) {
    new AckCommand(new Object()) {
      @Override
      protected getJobManager() {
        JobManager jobMgr = new JobManager("") {
          @Override
          def allJobs() {
            return completedJobs
          }
        }
        return jobMgr
      }
    }
  }

  @Test
  public void should_find_latest_failed_job() {
    def completedJobs = []

    // No jobs
    assert null == cmdWithCompletedJobs(completedJobs).findLatestFailedJob()

    // All successful
    completedJobs = [ job(true, 10), job(true, 500), job(true, 1000)]
    assert cmdWithCompletedJobs(completedJobs).findLatestFailedJob() == null

    // Only failed job
    def targetJob = job(false, 100)
    completedJobs = [ job(true, 10), targetJob, job(true, 500)]
    assert targetJob == cmdWithCompletedJobs(completedJobs).findLatestFailedJob()

    // Latest failed job
    completedJobs = [ targetJob, job(false, 90), job(false, 50)]
    assert targetJob == cmdWithCompletedJobs(completedJobs).findLatestFailedJob()
  }

  @Test
  public void should_ack_latest_failed_job_to_current_user() {
    def acked = [:]
    CommandFactory.metaClass.static.make = { String cmd, SlackHandler handler ->
      cmdWithCompletedJobs([ job(false, 100), job(false, 5678)])
    }
    AckManager.instance.metaClass.ack = { JobManager mgr, String jobId, String user -> acked[jobId] = user; return true }

    assert sh.handleCommand(bot, [text: "ack <@bot1>", user:user('fred')]) == "<@fred> has acked 'some.job-5678'"
    assert acked['some.job-5678'] == user('fred')
  }

  @Test
  public void should_fail_when_no_latest_failed_job() {
    def acked = [:]
    CommandFactory.metaClass.static.make = { String cmd, SlackHandler handler ->
      cmdWithCompletedJobs([ job(true, 100), job(true, 5678)])
    }

    assert sh.handleCommand(bot, [text: "ack <@bot1>", user:user('fred')]) == "No job found to ack"
    assert acked == [:]
  }


  private Job job(id) {
    new Job(id:id, failed:1)
  }
  private Job job(successful, completedDate, id=null) {
    new Job(currentStatus: JobStatus.COMPLETED, failed: successful ? 0 : 1,
            completedDate: new Date(completedDate), id: id == null ? "some.job-"+completedDate : id )
  }

}

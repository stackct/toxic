package toxic.slack.command

import toxic.slack.SlackHandler
import org.junit.*
import toxic.user.*
import toxic.job.*

@Mixin(UserManagerTestMixin)
public class PerformCommandTest extends CommandTest {
  @Before
  public void before() {
    super.before()
    userManagerSetup()
  }

  @After
  public void after() {
    userManagerTeardown()
    CommandFactory.metaClass = null
    super.after()
  }

  @Test
  public void should_return_usage_if_help() {
    assert sh.handleCommand(bot, [text: ".perform help"]) == "perform <job-id> <action>"
  }

  def cmdWithJobs(jobs) {
    new PerformCommand(new Object()) {
      @Override
      protected getJobManager() {
        JobManager jobMgr = new JobManager("") {
          @Override
          def allJobs() {
            return jobs
          }
        }
        return jobMgr
      }
    }
  }

  @Test
  public void should_perform_action_on_latest_job() {
    CommandFactory.metaClass.static.make = { String cmd, SlackHandler handler ->
      cmdWithJobs([ job(100), job(5678), job(200)])
    }
    assert sh.handleCommand(bot, [text: ".perform some release", user:user('fred')]) == "Action completed for 5678"
  }

  @Test
  public void should_respond_with_invalid_job() {
    CommandFactory.metaClass.static.make = { String cmd, SlackHandler handler ->
      cmdWithJobs([ job(100), job(5678), job(200), job(300)])
    }
    assert sh.handleCommand(bot, [text: ".perform some.job-500 release", user:user('fred')]) == "invalid job id: some.job-500"
  }

  @Test
  public void should_perform_action_on_specific_job() {
    CommandFactory.metaClass.static.make = { String cmd, SlackHandler handler ->
      cmdWithJobs([ job(100), job(5678), job(200), job(300)])
    }
    assert sh.handleCommand(bot, [text: ".perform some.job-200 release", user:user('fred')]) == "Action completed for 200"
  }

  private Job job(completedDate, id=null) {
    def job = new Job(completedDate: new Date(completedDate), id: id == null ? "some.job-"+completedDate : id )
    job.metaClass.performAction = { String action, String auth ->
      return "Action completed for " + completedDate
    }
    return job
  }

}

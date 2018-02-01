package toxic.slack.command

import org.junit.*
import toxic.job.*

@Mixin(UserManagerTestMixin)
public class ResolveCommandTest extends CommandTest { 
  def mockUsers = []

  @Before
  public void before() {
    super.before()
    userManagerSetup()
  }

  @After
  public void after() {
    AckManager.instance.metaClass = null
    userManagerTeardown()
    super.after()
  }

  @Test
  public void should_return_usage_if_no_args() {
    assert sh.handleCommand(bot, [text: "resolve <@bot1>"]) == "resolve <job-id>"    
  }  

  @Test
  public void should_fail_if_user_not_found() {
    assert sh.handleCommand(bot, [text: "resolve some.job-1234 <@bot1>", user:'UNKNOWN']) == "User could not be found 'UNKNOWN'"
  }

  @Test
  public void should_handle_failed_resolve() {
    AckManager.instance.metaClass.resolve = { JobManager mgr, String jobId, String user -> return false }
    assert sh.handleCommand(bot, [text: "resolve some.job-1234 <@bot1>", user:'U1234567']) == "Could not resolve job 'some.job-1234'"
  }

  @Test
  public void should_resolve_job() {
    def resolved = [:]

    AckManager.instance.metaClass.resolve = { JobManager mgr, String jobId, String user -> resolved[jobId] = user; return true }

    assert sh.handleCommand(bot, [text: "resolve some.job-1234 <@bot1>", user:user('fred')]) == "<@fred> has resolved 'some.job-1234'"
    assert resolved['some.job-1234'] == user('fred')
  }

  private Job job(id) {
    new Job(id:id, failed:1)
  }
}

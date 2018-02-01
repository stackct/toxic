package toxic.slack

import javax.activation.*
import javax.mail.*
import javax.mail.internet.*
import com.sun.mail.smtp.*

import template.*

import javax.servlet.http.*
import org.junit.*
import toxic.*
import toxic.user.*
import toxic.job.*
import toxic.slack.*
import toxic.web.*
import toxic.slack.command.*

@Mixin(UserManagerTestMixin)
public class ToxicSlackHandlerTest {
  def sh
  def jm
  def job
  def projects
  def jobs
  def urlText
  def url
  def bot
  def ran
  def result
  def currentPropsCalled
  def jobPropsCalled
  def sent = []
  def customProps = [:]
  
  @Before
  void before() {
    userManagerSetup()
    reset()
    SMTPTransport.metaClass.sendMessage = { Message msg, Address[] to -> sent << msg }
    SMTPTransport.metaClass.connect = { -> }
    SMTPTransport.metaClass.getLastServerResponse = { -> return "123-abc" }

    currentPropsCalled = 0
    jobPropsCalled = 0
    
    customProps.put("mail.host", "host1")
    customProps.put("path", "conf/templates")
    customProps.put("slack.maxCachedMessagesPerUser", "250")
    customProps.put("slack.triggeredEmailRecentRegex", "OPENED - Incident #([0-9]+) for \\((.+)\\): (.*)\$")
    customProps.put("slack.triggeredEmailPatternRegex", "(vtq|Vtq|VTQ) #incident([0-9]+)\$")
    customProps.put("slack.triggeredEmailPatternSearchCriteriaGroup", "2")
    customProps.put("slack.triggeredEmailTemplate", "triggered-email")
    customProps.put("slack.triggeredEmailRecipient", "triggered@mycompany.invalid")
    customProps.put("slack.triggeredEmailRecentRegexBodyGroup", "3")
    customProps.put("slack.triggeredEmailRecentRegexSubjectGroup", "2")
    customProps.put("slack.triggeredEmailBodyMaxLength", "15")
    customProps.put("slack.triggeredEmailSubjectMaxLength", "10")

    jm = new JobManager("url", "jobdir") {
      def findJob(id) {
        if(jobs && jobs[id]) {
          return jobs[id]
        }
        return job
      }
      def browseJobs() { return jobs.values() }
      def browseProjects() { return projects }
      String runJob(Job job) { ran = job; return result }
      def currentProperties() { currentPropsCalled++; super.currentProperties() }
      def jobProperties() { jobPropsCalled++; super.jobProperties() + customProps }
    }
    def ws = new WebServer(jm, 8001) {
      String getServerUrl() {
        return "http://foo:8001"
      }
    }

    ToxicServer.setServer(new ToxicServer([:]))
    ToxicServer.server.services = [ jm, ws ]

    bot = new SlackBot()
    bot.rtm = [self:[id:'bot1'], channels:[], groups:[]]
    sh = new ToxicSlackHandler(jm, ws)
    URL.metaClass.getText = { -> url = delegate; "urlText" }
    UrlTokenizer.instance.metaClass = null
  }
  
  @After
  void after() {
    userManagerTeardown()
    reset()
    URL.metaClass = null
    ToxicServer.reset()
    UrlTokenizer.instance.metaClass = null
    SlackBot.metaClass = null
  }
  
  void reset() {
    sent.clear()
    SMTPTransport.metaClass = null
  }

  @Test
  void should_handle_incomplete_commands() {
    assert sh.handleCommand(bot, [text:""]) == null
    assert sh.handleCommand(bot, [text:"<@bot1>"]) == null
    assert sh.handleCommand(bot, [text:"foo"]) == null
    assert sh.handleCommand(bot, [text:"<@bot1>:help"]) == "available commands: " + CommandFactory.available().sort().join(", ")
    assert sh.handleCommand(bot, [text:"describe <@bot1>"]) == "describe <job-id> <max-failures:5>"
    assert sh.handleCommand(bot, [text:"describe x <@bot1>"]) == "invalid job id: x"
    assert sh.handleCommand(bot, [text:"Describe x <@bot1>"]) == "invalid job id: x"
    assert sh.handleCommand(bot, [text:"<@bot1> halt"]) == "halt <job-id>"
    assert sh.handleCommand(bot, [text:"<@bot1> - halt x"]) == "job not running: x"
    assert sh.handleCommand(bot, [text:"<@bot1>-list"]) == "no projects available"
    assert sh.handleCommand(bot, [text:" <@bot1>run"]) == "run <job-id>"
    assert sh.handleCommand(bot, [text:"run x<@bot1>"]) == "invalid job id: x"
    assert sh.handleCommand(bot, [text:"pause <@bot1>"]) == "pause <project-name|all>"
    assert sh.handleCommand(bot, [text:"unpause <@bot1>"]) == "unpause <project-name|all>"
    assert sh.handleCommand(bot, [text:"<@bot1> oncall help"]) == "oncall [team:current-channel]\nLists personnel currently on call for the given team."
    assert sh.handleCommand(bot, [text:"<@bot1> log"]) == "log <trace|debug|info|warn|error>"
  }

  @Test
  void should_handle_dot_commands() {
    CommandFactory.metaClass.'static'.available = { -> ['foo'] }

    CommandFactory.metaClass.'static'.make = { String cmd, SlackHandler handler -> 
      [handle: { a,b,m -> "OK" }] as Command
    }

    assert sh.handleCommand(bot, [text: ".foo"])  == "OK"
    assert sh.handleCommand(bot, [text: ".Foo"])  == "OK"
    assert sh.handleCommand(bot, [text: ". foo"]) == "OK"
    assert sh.handleCommand(bot, [text: "."])     == null

    CommandFactory.metaClass = null
  }

  @Test
  public void should_add_user_if_not_exists() {

    CommandFactory.metaClass.'static'.available = { -> ['foo'] }

    CommandFactory.metaClass.'static'.make = { String cmd, SlackHandler handler -> 
      [handle: { a,b,m -> "OK" }] as Command
    }

    bot.metaClass.find = { String id, Map options=[:] -> 
      new User(id:'W9999999', name:'betty', profile:[:])
    }

    sh.handleCommand(bot, [text: "foo <@bot1>", user:'W9999999'])

    CommandFactory.metaClass = null
    
    assert user('betty') == 'W9999999'
  }

  @Test
  void should_handle_emailed_message() {

    CommandFactory.metaClass.'static'.available = { -> ['foo'] }

    CommandFactory.metaClass.'static'.make = { String cmd, SlackHandler handler -> 
      [handle: { a,b,m -> "${cmd}OK".toString() }] as Command
    }

    assert sh.handleCommand(bot, [text: "foo", file:[mode:'email',subject:'.foo']])  == "fooOK"
    assert sh.handleCommand(bot, [text: "foo", file:[mode:'email',subject:'foo']])  == "fooOK"
    assert sh.handleCommand(bot, [text: "foo", file:[mode:'email',subject:'bar',plain_text:'.foo']])  == "fooOK"
    assert sh.handleCommand(bot, [text: "foo", file:[mode:'email',subject:'foo',plain_text:'bar']])  == "fooOK"
    assert sh.handleCommand(bot, [text: "foo", file:[mode:'',subject:'foo',plain_text:'bar']])  == null

    CommandFactory.metaClass = null
  }

  @Test
  void should_detect_sensitive_data() {
    assert !sh.handleCommand(bot, [text: 'this is not sensitive'])
    assert !sh.handleCommand(bot, [text: 'this 1234567890 is not sensitive'])
    assert !sh.handleCommand(bot, [text: 'this 12345678901234567890 is sensitive'])
    assert sh.handleCommand(bot, [text: 'this 12345678901234 is sensitive'])
    assert sh.handleCommand(bot, [text: 'this 1234567890123456 is sensitive'])
    assert sh.handleCommand(bot, [text: 'this 1234567890123456789 is sensitive'])
    assert sh.handleCommand(bot, [text: '1234567890123456'])
    assert !sh.handleCommand(bot, [text: 'http://xyz.com/1234567890123456'])
    assert !sh.handleCommand(bot, [text: 'https://xyz.com/1234567890123456'])
    assert !sh.handleCommand(bot, [text: 'check it out http://xyz.com/1234567890123456'])
  }

  @Test
  void should_detect_long_url() {
    int max = 100

    def http = "http://"
    def https = "https://"

    checkLongUrl('foo', false)
    checkLongUrl('http://foo', false)
    checkLongUrl('http://url-with-slug-and-params?x=1&y=0', false)
    checkLongUrl(makeUrl(http, max) + '-with-slugs?x=1&y=0', true)
    checkLongUrl(makeUrl(http, max), false)
    checkLongUrl(makeUrl(https, max), false)
    checkLongUrl(makeUrl(http, max+1), true)
    checkLongUrl(makeUrl(https, max+1), true)
    checkLongUrl('Click here to see my awesome link! ' + makeUrl(http, max), false)
    checkLongUrl('Click here to see my awesome link! ' + makeUrl(http, max+1), true)
    checkLongUrl("x".multiply(max) + ' and ' + makeUrl(https, max), false)
  }

  private makeUrl(prefix, length) {
    prefix + "x".multiply(length - prefix.size())
  }

  private checkLongUrl(message, expected) {
    boolean messageSent = false
    bot.metaClass.sendMessage = { String c, String s -> messageSent = true }
    sh.checkForLongUrl(bot, [text: message])
    assert messageSent == expected
    bot.metaClass = null
  }

  @Test
  void should_fetch_external_url() {
    def msg = [user:'u1',channel:'c1',text:'hi there & bye']
    assert !sh.fetchExternalUrl(msg)
    
    jm.currentProperties()."job.slack.externalUrl" = "http://somewhere"
    assert sh.fetchExternalUrl(msg).text == "urlText"
    assert url.toString() == "http://somewhere"

    jm.currentProperties()."job.slack.externalUrl" = "http://somewhere"
    jm.currentProperties()."job.slack.externalUrlIncludeMsg" = "TRUE"
    assert sh.fetchExternalUrl(msg).text == "urlText"
    assert url.toString() == "http://somewhere?c=c1&u=u1&t=hi+there+%26+bye"

    jm.currentProperties()."job.slack.externalUrl" = "http://somewhere?someparam=1"
    jm.currentProperties()."job.slack.externalUrlIncludeMsg" = "TRUE"
    assert sh.fetchExternalUrl(msg).text == "urlText"
    assert url.toString() == "http://somewhere?someparam=1&c=c1&u=u1&t=hi+there+%26+bye"
  }
  
  @Test
  void should_remove_user_alert() {
    assert sh.removeUserAlert("hello @some dude") == "hello s.ome dude"
    assert sh.removeUserAlert("@js_mith") == "j.s_mith"
    assert sh.removeUserAlert("hello") == "hello"
    assert sh.removeUserAlert("@Hello") == "@Hello"
    assert sh.removeUserAlert("@") == "@"
    assert sh.removeUserAlert("@h oh") == "@h oh"
  }

  @Test
  void should_pull_cloned_job_props() {
    assert !jobPropsCalled
    sh.config('secure.foo')
    assert jobPropsCalled
  }

  @Test
  void should_send_email() {    
    sh.sendEmail("triggered-email", "test@mycompany.invalid", "asubject", "abody")

    assert sent.size() == 1
    assert sent[0].from[0].address == "sample@mycompany.invalid"
    assert sent[0].from[0].personal == "First Last"
    assert sent[0].replyTo[0].address == "sample@mycompany.invalid"
    assert sent[0].replyTo[0].personal == "First Last"
    assert sent[0].getSubject() == "asubject"
    assert sent[0].getRecipients(Message.RecipientType.TO)[0].address == "test@mycompany.invalid"
    assert sent[0].content.getBodyPart(0).contentType == "text/plain; charset=utf-8"
    assert sent[0].content.getBodyPart(0).content == "abody"
  }

  @Test
  void should_trigger_email() {
    def expected = "Sent triggered email to triggered@mycompany.invalid"

    assert !sh.handleCommand(bot, [user: 'id1', text: "nothing important"])
    assert !sh.handleCommand(bot, [user: 'id1', text: "OPENED - Incident #1234 for (this is another test): more incident details"])
    assert sent.size() == 0
    assert expected == sh.handleCommand(bot, [user: 'id1', text: "vtq #incident1234"])
    assert sent.size() == 1
    assert sent[0].subject == "this is an"
    assert sent[0].content.getBodyPart(0).content == "more incident d"
  }

  @Test
  void should_trigger_email_from_bot_attachment() {
    def expected = "Sent triggered email to triggered@mycompany.invalid"

    assert !sh.handleCommand(bot, [bot_id: 'id1', attachments:[[text: "nothing important"]]])
    assert !sh.handleCommand(bot, [bot_id: 'id1', attachments:[[text: "OPENED - Incident #1234 for (this is another test): more incident details"]]])
    assert sent.size() == 0
    assert expected == sh.handleCommand(bot, [bot_id: 'id1', attachments:[[text: "vtq #incident1234"]]])
    assert sent.size() == 1
    assert sent[0].subject == "this is an"
    assert sent[0].content.getBodyPart(0).content == "more incident d"
  }

  @Test
  void should_fail_to_trigger_email() {
    def expected = ":warning: No matching message found; unable to trigger email"

    assert !sh.handleCommand(bot, [user: 'id1', text: "nothing important"])
    assert expected == sh.handleCommand(bot, [user: 'id1', text: "vtq #incident1234"])
    assert sent.size() == 0
  }
}

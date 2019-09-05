package toxic.slack

import org.junit.*
import toxic.*
import toxic.job.*
import toxic.slack.*

class SlackNotificationTest {
  @After
  void after() {
    SlackBot.metaClass = null
  }

  @Test
  void should_notify() {
    def sn = new SlackNotification()
    sn.job = new Job()
    assert !sn.shouldNotify()

    sn.job.properties = new ToxicProperties()
    sn.job.results << new TaskResult(family:"test4", name:"task4a", startTime:2, stopTime:6)
    sn.job.failed = 1
    sn.job.suites = 1
    sn.jobSimple = sn.job.toSimple()
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.channels"] = "general"
    assert sn.shouldNotify()

    sn.job.properties["job.slack.users"] = "user1"
    assert sn.shouldNotify()

    sn.job.properties["job.slack.channels"] = ""
    assert sn.shouldNotify()

    sn.job.properties["job.slack.onsuccess"] = "false"
    assert sn.shouldNotify()

    sn.job.properties["job.slack.onfailure"] = "false"
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onfirstsuccess"] = "false"
    assert !sn.shouldNotify()

    sn.job.failed = 0
    sn.jobSimple = sn.job.toSimple()
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onfailure"] = "true"
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onfirstsuccess"] = "true"
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onsuccess"] = "true"
    assert sn.shouldNotify()

    SlackBot.instance.silenceUntil(System.currentTimeMillis() + 10000)
    assert !sn.shouldNotify()
    SlackBot.instance.silenceUntil(System.currentTimeMillis() - 10000)
    assert sn.shouldNotify()

    sn.job.failed = 0
    sn.job.previousJob = new Job(failed: 0)
    sn.jobSimple = sn.job.toSimple()
    sn.job.properties["job.slack.onsuccess"] = "false"
    sn.job.properties["job.slack.onfailure"] = "false"
    sn.job.properties["job.slack.onfirstsuccess"] = "false"
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onfirstsuccess"] = "true"
    assert !sn.shouldNotify()

    sn.job.failed = 0
    sn.job.previousJob = new Job(failed: 1)
    sn.jobSimple = sn.job.toSimple()
    sn.job.properties["job.slack.onsuccess"] = "false"
    sn.job.properties["job.slack.onfailure"] = "false"
    sn.job.properties["job.slack.onfirstsuccess"] = "false"
    assert !sn.shouldNotify()

    sn.job.properties["job.slack.onfirstsuccess"] = "true"
    assert sn.shouldNotify()
  }

  @Test
  void should_generate_blame_list() {
    def sn = new SlackNotification() {
      def linkJob(def job) { "" }
    }
    def job = new Job(id: 'test', commits: [[user:'me'], [user: 'you']], failed: 1)
    sn.job = job
    sn.jobSimple = [failed: 1]
    SlackBot.metaClass.'static'.getInstance = { -> return new Object() { def findUser(a,b,c) { return [id:a] }}}

    def blames = sn.blameList(job)
    assert blames.contains("Commit by <@me>")
    assert blames.contains("Commit by <@you>")
  }

  @Test
  void should_not_blame_repeatedly() {
    def sn = new SlackNotification() {
      def linkJob(def job) { "" }
    }
    sn.job = new Job(id: 'test', commits: [[user:'me']], failed: 1)
    sn.jobSimple = [failed: 1]
    SlackBot.metaClass.'static'.getInstance = { -> return new Object() { def findUser(a,b,c) { return [id:'x'] }}}
    assert sn.getOutcome().split("\n").size() == 2
  }

  @Test
  void should_generate_failure_list() {
    def sn = new SlackNotification() {
      def linkJob(def job) { "" }
    }
    def job = new Job(id: 'test', commits: [[user:'me'], [user: 'you']], failed: 1)
    job.metaClass.getSimpleResults = { ->
      [
        'suiteA': [['suite': 'suiteA', 'family': 'famA', 'name': 'nameA', 'success': false, 'startTime': 100, 'stopTime': 100]],
        'suiteB': [['suite': 'suiteB', 'family': 'famA', 'name': 'nameA', 'success': false, 'startTime': 100, 'stopTime': 100]],
        'suiteC': [['suite': 'suiteC', 'family': 'famA', 'name': 'nameA', 'success': false, 'startTime': 100, 'stopTime': 100]],
        'suiteD': [['suite': 'suiteD', 'family': 'famA', 'name': 'nameA', 'success': false, 'startTime': 100, 'stopTime': 100]],
      ]
    }

    def fails = sn.failureList(job, 6)
    ["suiteA","suiteB","suiteC","suiteD"].each {assert fails.contains("Failed: ${it}")}

    fails = sn.failureList(job, 3)
    ["suiteA","suiteB"].each {assert fails.contains("Failed: ${it}")}
    assert fails.contains(" ... and 1 more")

  }

  @Test
  public void should_generate_correct_content() {
    SlackBot.instance.handler = null
    def sn = new SlackNotification()
    sn.job = new Job()

    sn.job.properties = new ToxicProperties()
    sn.job.id = "quality.job-1"
    sn.job.failed = 1
    sn.job.suites = 5
    sn.jobSimple = sn.job.toSimple()
    assert sn.getOutcome() ==~ /quality\.job-1: \*FAILED \[1\]\*.*/

    sn.job.failed = 0
    sn.job.suites = 5
    sn.jobSimple = sn.job.toSimple()
    assert sn.getOutcome() ==~ /quality\.job-1: \*SUCCESS \[5\]\*.*/
  }
}

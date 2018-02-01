package toxic.job

import org.junit.*
import toxic.*

class SmtpNotificationTest {
  @Test
  void should_get_body() {
    def sn = new SmtpNotification()
    sn.job = new Job()
    sn.job.id = "my.job-0"
    sn.maxFailures = 2
    sn.job.properties = new ToxicProperties()
    sn.job.results << new TaskResult(family:"test1", name:"task1a", startTime:2, stopTime:3)
    sn.job.results << new TaskResult(family:"test1", name:"task1b", startTime:2, stopTime:3)
    sn.job.results << new TaskResult(family:"test2", name:"task2a", startTime:2, stopTime:6)
    sn.job.results << new TaskResult(family:"test3", name:"task3a", startTime:2, stopTime:6, success: true)
    sn.job.results << new TaskResult(family:"test4", name:"task4a", startTime:2, stopTime:6)
    sn.job.failed = 3
    sn.job.suites = 4
    sn.jobSimple = sn.job.toSimple()

    def actual = sn.contentText
    def expected = """my.job-0: FAILED

test1 (2): 2ms
test2 (1): 4ms
...
"""
    assert actual == expected
  }
  
  @Test
  void should_mail() {
    def sn = new SmtpNotification()
    sn.job = new Job()
    assert !sn.shouldMail()

    sn.job.properties = new ToxicProperties()
    sn.job.results << new TaskResult(family:"test4", name:"task4a", startTime:2, stopTime:6)
    sn.job.failed = 1
    sn.job.suites = 1
    sn.jobSimple = sn.job.toSimple()
    assert !sn.shouldMail()

    sn.job.properties["job.smtp.host"] = "somehost"
    assert !sn.shouldMail()

    sn.job.properties["job.smtp.recipients"] = "someone@somehost.com"
    assert sn.shouldMail()
  
    sn.job.properties["job.smtp.onsuccess"] = "false"
    assert sn.shouldMail()

    sn.job.properties["job.smtp.onfailure"] = "false"
    assert !sn.shouldMail()

    sn.job.failed = 0
    sn.jobSimple = sn.job.toSimple()
    assert !sn.shouldMail()
    sn.job.properties["job.smtp.onfailure"] = "true"
    assert !sn.shouldMail()
    sn.job.properties["job.smtp.onsuccess"] = "true"
    assert sn.shouldMail()
  }
}
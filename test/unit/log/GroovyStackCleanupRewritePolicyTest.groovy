package log

import org.apache.log4j.Appender
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import org.apache.log4j.WriterAppender
import org.apache.log4j.rewrite.RewriteAppender
import org.junit.Before
import org.junit.Test

class GroovyStackCleanupRewritePolicyTest {

  def thingThatLogs
  StringWriter outputHolder
  Appender inner

  private static class ThingThatLogs {
    Logger log

    def logSomething(msg) { log.info("You should not see this, replaced by metaclass") }
  }

  @Before
  void setup() {
    outputHolder = new StringWriter()
    inner = new WriterAppender(new PatternLayout("%m%n"), outputHolder)

    // Do some metaprogramming stuff
    ThingThatLogs.metaClass.changeMsg = { msg -> "meta-manipulated: " + msg}
    ThingThatLogs.metaClass.logSomething = { msg ->
      log.info(changeMsg(msg), new Exception("TestException"))
    }

    thingThatLogs = new ThingThatLogs()
  }

  private setAppender(a) {
    Logger log = Logger.getLogger("foo")
    log.addAppender(a)
    log.setAdditivity(false)
    thingThatLogs.log = log
  }

  @Test
  void verify_stacktrace_contains_groovy_stuff() {

    setAppender(inner)

    thingThatLogs.logSomething("This is a test")

    println("unfiltered appender output=${outputHolder}")
    def output = outputHolder.toString()
    assert output.contains("This is a test")
    assert output.contains("TestException")
    assert output.contains("at ${this.getClass().getName()}")
    assert output.contains("org.codehaus.groovy")
    assert output.contains("sun.reflect.")
    assert output.contains("java.lang.reflect.")
  }


  @Test
  void should_cleanse_stack_trace() {

    Appender rewriteAppender = new RewriteAppender()
    rewriteAppender.addAppender(inner)
    rewriteAppender.setRewritePolicy(new GroovyStackCleanupRewritePolicy())
    setAppender(rewriteAppender)

    thingThatLogs.logSomething("This is a test")

    println("appender output=${outputHolder}")
    def output = outputHolder.toString()
    assert output.contains("This is a test")
    assert output.contains("TestException")
    assert output.contains("at ${this.getClass().getName()}")
    assert !output.contains("org.codehaus.groovy")
    assert !output.contains("groovy.lang.")
    assert !output.contains("sun.reflect.")
    assert !output.contains("java.lang.reflect.")
    assert !output.contains("sun.proxy.")
  }
}
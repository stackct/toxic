package log

import org.junit.*
import static org.junit.Assert.*
import groovy.mock.interceptor.*

import org.apache.log4j.*

class StickyThreadAppenderTest {

  def layout
 
  @Before
  void before() {
    layout = new PatternLayout("%p: %m\n")
  }

  @Test
  void should_single_thread() {
    def writer = new StringWriter()
    def appender = new StickyThreadAppender(new WriterAppender(layout, writer))    

    def log = Log.getLogger(StickyThreadAppenderTest)
    log.addAppender(appender)

    log.info("Hello World")

    assert writer.toString().contains("Hello World")
  }

  /*
   * One logger, two threads, two appenders in one log hierarchy.
   */
  @Test
  void should_filter_multi_threaded() {
    def log = Log.getLogger(StickyThreadAppenderTest)

    def writer1 = new StringWriter()
    def writer2 = new StringWriter()

    def thread1 = startThread(log, writer1)
    def thread2 = startThread(log, writer2)

    thread1.join()
    thread2.join()

    assert writer1.toString().contains("Write from thread ${thread1.id}")
    assert writer2.toString().contains("Write from thread ${thread2.id}")
  }

  private startThread(log, writer) {
    Thread.start {
      def appender = new StickyThreadAppender(new WriterAppender(layout, writer))    
      log.addAppender(appender)
      log.info("Write from thread ${Thread.currentThread().id}")
      log.removeAppender(appender)
    }
  }
}

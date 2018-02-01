package log

import static org.junit.Assert.*
import groovy.mock.interceptor.*
import org.junit.*

import org.apache.log4j.*
import org.apache.log4j.spi.*

class LogCaptureTest {

  @Test
  void should_allow_log_info_to_be_captured() {
    def log = Log.getLogger(String)

    def initialAppenderCount = log.log4jLogger.allAppenders.iterator().size()

    def capture = LogCapture.factory(log).capture()

    def capturingAppenderCount = log.log4jLogger.allAppenders.iterator().size()
    assert initialAppenderCount == capturingAppenderCount - 1

    log.info("Hello World")

    assert capture.text == "INFO: Hello World\n"

    capture.close()

    log.info("Should be closed")

    def closedAppenderCount = log.log4jLogger.allAppenders.iterator().size()
    assert closedAppenderCount == initialAppenderCount

    assert !capture.text.contains("Should be closed")
    assert capture.text == "INFO: Hello World\n"
  }
  
  @Test
  void should_allow_access_to_logged_text_and_auto_remove_appender() {
    def log = Log.getLogger(String)

    def initialAppenderCount = log.log4jLogger.allAppenders.iterator().size()

    def text = LogCapture.factory(log).capture() {
      log.info("Hello World")
    }

    log.info("Should be closed")

    def closedAppenderCount = log.log4jLogger.allAppenders.iterator().size()

    assert !text.contains("Should be closed")
    assert text == "INFO: Hello World\n"
  }

  @Test
  void should_include_messages_that_contain_certain_text() {
    def log = Log.getLogger(String)

    def capture = LogCapture.factory(log)
    capture.includes << "Hello World"
    capture.includes << "Goodbye World"

    def text = capture.capture() {
      log.info("Hello World")
      log.info("Filter this out")
      log.info("Goodbye World")
    }

    assert text.contains("Hello World")
    assert text.contains("Goodbye World")
    assert !text.contains("Filter this out")
  }

  @Test
  void should_not_fail_if_the_includes_property_is_set_to_null() {
    def log = Log.getLogger(String)

    def capture = LogCapture.factory(log)
    capture.includes = null

    def text = capture.capture() {
      log.info("Hello World")
    }

    assert text.contains("Hello World")
  }
}


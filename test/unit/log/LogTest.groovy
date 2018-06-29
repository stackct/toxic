package log

import org.junit.*
import static org.junit.Assert.*
import groovy.mock.interceptor.*

import org.apache.log4j.*
import org.apache.log4j.spi.*

class LogTest {

  @Before
  void before() {
    Log.xml4jConfigPath = { null }
    Log.reconfigure()
    Log.scrubPatterns[/(?iu)Error/] = "Err"
    Log.scrubPatterns[/(?iu)Exception/] = "Exc"
  }

  @After
  void after() {
    Log.reset()
  }

  @Test
  void should_format_xml_string_into_pretty_xml() {
    def xml = "<a><b d='e' f=\"g\">c</b><h/><i></i></a>"

    def expected = ""
    expected += "<a>\n"
    expected += "  <b d=\"e\" f=\"g\">c</b>\n"
    expected += "  <h/>\n"
    expected += "  <i/>\n"
    expected += "</a>\n"

    assert Log.formatXml(xml) == expected
  }

  @Test
  void should_format_xml_that_is_invalid() {
    assert "" == Log.formatXml(null)
    assert "this is not xml" == Log.formatXml("this is not xml")
  }

  @Test
  void should_default_to_simple_logging() {
    Log log = Log.getLogger(LogTest.class)
    assert Log.isConfigured()
    assert Log.isUsingSimpleLogging()

    Log log2 = Log.getLogger(Log.class)
    assert Log.isConfigured()
    assert Log.isUsingSimpleLogging()
  }

  @Test
  void should_construct_using_a_class() {
    Log log = Log.getLogger(LogTest.class)
  }

  @Test
  void should_construct_using_a_string() {
    Log log = Log.getLogger("some logger")
  }

  @Test
  void should_default_to_none_level() {
    Log log = Log.getLogger(LogTest.class)
    assert log.getLevel() == Log.LEVEL_NONE
  }

  @Test
  void should_set_to_error_level() {
    Log log = Log.getLogger(Random.class)
    log.setLevel(Log.LEVEL_ERROR)
    assert log.error
    assert !log.warn
    assert !log.info
    assert !log.debug
    assert !log.trace
  }

  @Test
  void should_set_to_warn_level() {
    Log log = Log.getLogger(LogTest.class)
    log.setLevel(Log.LEVEL_WARN)
    assert log.error
    assert log.warn
    assert !log.info
    assert !log.debug
    assert !log.trace
  }

  @Test
  void should_set_to_info_level() {
    Log log = Log.getLogger(Random.class)
    log.setLevel(Log.LEVEL_INFO)
    assert log.error
    assert log.warn
    assert log.info
    assert !log.debug
    assert !log.trace
  }

  @Test
  void should_set_to_debug_level() {
    Log log = Log.getLogger(LogTest.class)
    Log log2 = Log.getLogger(Integer.class)

    log.setLevel(Log.LEVEL_DEBUG)
    assert log.getLevel() == Log.LEVEL_DEBUG
    assert log.error
    assert log.warn
    assert log.info
    assert log.debug
    assert !log.trace

    // Quick sanity check to make sure setting one logger's level doesn't affect another's
    assert log2.getLevel() == Log.LEVEL_NONE
    assert log2.error
    assert log2.warn
    assert log2.info
    assert log2.debug
    assert log2.trace
  }

  @Test
  void should_set_to_trace_level() {
    Log log = Log.getLogger(Random.class)
    log.setLevel(Log.LEVEL_TRACE)
    assert log.error
    assert log.warn
    assert log.info
    assert log.debug
    assert log.trace
  }

  @Test
  void should_set_root_to_debug_level() {
    Log log = Log.getLogger(String.class)
    Log.setRootLevel(Log.LEVEL_DEBUG)
    assert log.getLevel() == Log.LEVEL_NONE
    assert log.error
    assert log.warn
    assert log.info
    assert log.debug
    assert !log.trace
  }

  @Test
  void should_set_root_to_debug_level_via_string() {
    Log log = Log.getLogger(String.class)
    Log.setRootLevel("deBUG")
    assert log.getLevel() == Log.LEVEL_NONE
    assert log.error
    assert log.warn
    assert log.info
    assert log.debug
    assert !log.trace
  }

  @Test
  void should_set_logger_to_debug_level_via_string() {
    Log.setLevel(String.class.name, 'debug')
    Log log = Log.getLogger(String.class)
    assert log.getLevel() == Log.LEVEL_DEBUG
    assert log.error
    assert log.warn
    assert log.info
    assert log.debug
    assert !log.trace
  }

  @Test
  void should_convert_level() {
    assert Log.LEVEL_TRACE == Log.convertLevel("xyz")
    assert Log.LEVEL_TRACE == Log.convertLevel("traCE")
    assert Log.LEVEL_DEBUG == Log.convertLevel("debug")
    assert Log.LEVEL_DEBUG == Log.convertLevel("DEBUG")
    assert Log.LEVEL_INFO == Log.convertLevel("info")
    assert Log.LEVEL_INFO == Log.convertLevel("INFO")
    assert Log.LEVEL_WARN == Log.convertLevel("warn")
    assert Log.LEVEL_WARN == Log.convertLevel("WARN")
    assert Log.LEVEL_ERROR == Log.convertLevel("error")
    assert Log.LEVEL_ERROR == Log.convertLevel("ERROR")
  }

  @Test
  void should_add_appender() {
    def log = Log.getLogger(String)

    def writer = new StringWriter()
    def appender = new WriterAppender(new PatternLayout("%p: %m"), writer)
    
    log.addAppender(appender)

    log.info("Hello World")
    log.debug("Goodbye World")

    assert writer.toString().contains("INFO: Hello World")
    assert writer.toString().contains("DEBUG: Goodbye World")

    log.removeAppender(appender)

    log.info("Should not see this")

    assert !writer.toString().contains("Should not see this")
  }

  @Test
  void should_capture_stdout_and_error() {
    def text = Log.captureStdOut {
      System.out.println "Hello World"
      System.err.println "Goodbye World"
    }

    assert text.contains("Hello World")
    assert text.contains("Goodbye World")
  }

  @Test
  void should_capture_log_messages_with_hash_declared_logger() {
    def log = Log.getLogger(String)
    def capture = Log.capture(logger: log)

    log.info("Hello World")

    capture.close()

    assert capture.text == "INFO: Hello World\n"
  }

  @Test
  void should_allow_capture_to_accept_single_logger_argument() {
    def log = Log.getLogger(String)
    def capture = Log.capture(log)

    log.info("Hello World")

    capture.close()

    assert capture.text == "INFO: Hello World\n"
  }

  @Test
  void should_allow_capture_to_accept_actual_log4j_instance() {
    def log = Log.getLogger(String)
    def capture = Log.capture(log.log4jLogger)

    log.info("Hello World")

    capture.close()

    assert capture.text == "INFO: Hello World\n"
  }

  @Test
  void should_capture_log_messages_using_default_root_logger() {
    def capture = Log.capture()

    def log = Log.getLogger(String)
    log.info("Hello World")

    capture.close()

    assert capture.text == "INFO: Hello World\n"
  }
  
  @Test
  void should_capture_log_messages_and_return_the_text() {
    def log = Log.getLogger(String)

    def text = Log.capture(logger: log) {
      log.info("Hello World")
    }

    assert text == "INFO: Hello World\n"
  }

  @Test(expected = IllegalArgumentException)
  void should_fail_if_logger_is_not_a_supported_type() {
    Log.capture("NOT A LOGGER")
  }

  @Test
  void should_capture_log_messages_using_root_logger_in_a_closure() {
    def text = Log.capture() {
      def log = Log.getLogger(String)
      log.info("Hello World")
    }

    assert text == "INFO: Hello World\n"
  }

  @Test
  void should_capture_log_messages_with_certain_words() {
    def log = Log.getLogger(String)
    def text = Log.capture(logger: log, includes: ["Tree"]) {
      log.info("Dont see this")
      log.info("Climb the Tree")
    }

    assert text == "INFO: Climb the Tree\n"
  }

  @Test
  void should_capture_log_output_but_line_terminate_with_vertical_vars_for_admin_scripts() {
    def log = Log.getLogger(String)

    def text = Log.captureForAdminResponse(logger: log) {
      log.info("Hello World")
      log.info("Goodbye World")
      log.info("Comfortably Numb")
    }

    assert text == "INFO: Hello World|INFO: Goodbye World|INFO: Comfortably Numb|"
  }

  @Test
  void should_capture_certain_words_in_client_response() {
    def log = Log.getLogger(String)

    def text = Log.captureForAdminResponse(logger: log, includes: ["awesome"]) {
      log.info("Hello World")
      log.info("You are awesome")
    }

    assert text == "INFO: You are awesome|"
  }

  @Test
  void should_scrub_keywords() {
    def capture = Log.capture()

    def log = Log.getLogger(String)
    log.info("Hello World, this is an Exception Error exception error EXCEPTION ERROR")

    capture.close()

    assert capture.text == "INFO: Hello World, this is an Exc Err Exc Err Exc Err\n"
  }

  @Test
  void should_not_scrub_keywords_if_null() {
    def capture = Log.capture()

    def log = Log.getLogger(String)
    log.info(null)

    capture.close()

    assert capture.text == "INFO: \n"
  }
}



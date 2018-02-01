package log

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

class LogTrackerTest {
  private Log log

  @Before
  void setUp() {
    Log.xml4jConfigPath = { null }
    Log.reconfigure()
    Log.scrubPatterns[/(?iu)Error/] = "Err"
    Log.scrubPatterns[/(?iu)Exception/] = "Exc"

    log = Log.getLogger(LogTrackerTest)
  }

  @After
  void tearDown() {
    Log.reset()
  }

  @Test
  void should_construct_log_tracker() {
    def logger = Logger.getLogger("SOME LOGGER")
    def logTracker = new LogTracker(logger)
    assert 'SOME LOGGER' == logTracker.name
    assert logger == logTracker.originalLogger
  }


  @Test
  void should_interept_log_messages_for_test_verification() {
    Log log = Log.getLogger(Random.class)
    log.track { logger ->
      log.error "this is logged Err"
      log.warn  "this is logged warn"
      log.info  "this is logged info"
      log.debug "this is logged debug"
      log.trace "this is logged trace"

      assert logger.isLogged("this is logged Err")
      assert logger.isLogged("this is logged warn")
      assert logger.isLogged("this is logged info")
      assert logger.isLogged("this is logged debug")
      assert logger.isLogged("this is logged trace")

      assert !logger.isLogged("this is not logged")
    }
  }

  @Test
  void should_determine_if_messages_were_logged_at_specifed_level() {
    Log log = Log.getLogger(Random.class)
    log.track { logger ->

      log.error "this is logged error"
      assert logger.isLogged("this is logged Err", Level.ERROR)
      assert !logger.isLogged("this is logged Err", Level.TRACE)
      assert !logger.isLogged("this is logged Err", Level.DEBUG)
      assert !logger.isLogged("this is logged Err", Level.INFO)
      assert !logger.isLogged("this is logged Err", Level.WARN)
    }
  }

  @Test
  void should_determine_if_exception_was_included_in_message() {
    Log log = Log.getLogger(Random.class)
    log.track { logger ->

      log.error("log exception", new Exception('Oops'))
      log.error("log illegal argument", new IllegalArgumentException('Oops'))
      log.error('log exception without throwable', null)
      log.error('log exception with map and throwable', [foo:"bar \"ok\"", hi:"there"], new NullPointerException("npe"))

      assert logger.isLogged("log Exc", Level.ERROR, Exception.class)
      assert logger.isLogged("log illegal argument", Level.ERROR, IllegalArgumentException.class)
      assert !logger.isLogged("log illegal argument", Level.ERROR, Exception.class)
      assert logger.isLogged("log Exc without throwable", Level.ERROR, null)
      assert logger.isLogged("log Exc with map and throwable; foo=\"bar 'ok'\"; hi=\"there\"", Level.ERROR, NullPointerException.class)
    }
  }

  @Test
  void should_only_keep_the_last_X_messages() {
    Log log = Log.getLogger(Random.class)
    log.track { logger ->
      70.times { log.error "log ${it} messages" }

      assert !logger.isLogged("log 1 messages")
      assert !logger.isLogged("log 4 messages")

      assert logger.isLogged("log 60 messages")
      assert logger.isLogged("log 69 messages")
    }
  }
  @Test
  void reset_clears_entries() {
    log.track { tracker ->
      log.info("test1")
      log.info("test2")

      assert tracker.isLogged('test1')
      assert tracker.isLogged('test2')
      tracker.reset()

      assert !tracker.isLogged('test1')
      assert !tracker.isLogged('test2')
    }

  }

  @Test
  void isEnabledFor_reflects_underlying_logger() {
    def levels = [
      [(Level)Level.ERROR , Log.LEVEL_ERROR],
      [(Level)Level.WARN  , Log.LEVEL_WARN],
      [(Level)Level.INFO  , Log.LEVEL_INFO],
      [(Level)Level.DEBUG , Log.LEVEL_DEBUG],
      [(Level)Level.TRACE , Log.LEVEL_TRACE
]    ]
    log.track { tracker ->
      levels.each { level ->
        log.setLevel(level[1])

        assert tracker.isEnabledFor(level[0])
      }
    }
  }

  @Test
  void isLogged_finds_message_with_values() {
    def message = "test-message"
    def values = [testKey2: 'test2', testKey1: 'test1']

    log.track { tracker ->
      log.info(message, values)

      assert tracker.isLogged(message, values)
    }
  }

  @Test
  void isLogged_finds_message_with_values_and_priority() {
    def message = "test-message"
    def values = [testKey2: 'test2', testKey1: 'test1']

    log.track { tracker ->
      log.info(message, values)

      assert tracker.isLogged(message, values, Level.INFO)
    }
  }

  @Test
  void isLogged_does_not_find_message_with_values_and_wrong_priority() {
    def message = "test-message"
    def values = [testKey2: 'test2', testKey1: 'test1']

    log.track { tracker ->
      log.info(message, values)

      assert !tracker.isLogged(message, values, Level.WARN)
    }
  }
}

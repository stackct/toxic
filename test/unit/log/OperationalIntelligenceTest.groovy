package log

import org.junit.*
import static org.junit.Assert.*
import groovy.mock.interceptor.*

import util.*

class OperationalIntelligenceTest {

  @Before
  void before() {
    OperationalIntelligence.reset()
  }

  @Test
  void should_capture_targetted_OI_values() {
      def log = Log.getLogger(OperationalIntelligenceTest)

      def text

      DateTime.mock('1111-12-13 14:15:16.017', '1111-12-13 15:16:17.017').use {

        text = Log.capture(logger: log) {
          def intelligence = OperationalIntelligence.start("SOME_SUMMARY_NAME", log)

          // capture one of more key values
          OperationalIntelligence.log("SOME_SUMMARY_NAME", "key1", "value1")
          OperationalIntelligence.log("SOME_SUMMARY_NAME", "key2", "value2", "key3", "value3")

          // Track an attomic counter of an event
          3.times { OI.logCounter("SOME_SUMMARY_NAME", "someCounter") }

          // Use simpler class name and add a hash of intelligence.
          OI.log("SOME_SUMMARY_NAME", [key4: "value4"])

          // Cross thread capture.
          Thread.start { OI.log("SOME_SUMMARY_NAME", "key5", "value5") }.join()

          // Cross stack frames capture..
          someMethod { OI.log("SOME_SUMMARY_NAME", "key6", "value6") }

          // Log all key values captured since start.
          OperationalIntelligence.stop("SOME_SUMMARY_NAME")
        }
      }

      // Verify data
      assert text.contains("summary=\"SOME_SUMMARY_NAME\"")
      assert text.contains("someCounter=\"3\"")
      assert text.contains("start=\"1111-12-13 14:15:16.017\"")
      assert text.contains("end=\"1111-12-13 15:16:17.017\"")
      assert text.contains("elapsedHHMMSS=\"01:01:01\"")
      assert text.contains("key1=\"value1\"")
      assert text.contains("key2=\"value2\"")
      assert text.contains("key3=\"value3\"")
      assert text.contains("key4=\"value4\"")
      assert text.contains("key5=\"value5\"")
      assert text.contains("key6=\"value6\"")

      // Verify format
      assert text.contains("summary=\"SOME_SUMMARY_NAME\", key1=\"value1\", key2=\"value2\", key3=\"value3\", key4=\"value4\", key5=\"value5\", key6=\"value6\", start=\"1111-12-13 14:15:16.017\", end=\"1111-12-13 15:16:17.017\", elapsedHHMMSS=\"01:01:01\", someCounter=\"3\"")
  }

  private someMethod(closure) {
    closure()
  }

  @Test
  void should_reset_by_default() {
    OI.reset()

    assert OI.intelligence == [:]
  }

  @Test
  void should_reset_after_capturing_entries() {
    OI.start("summaryName")

    OI.reset()

    assert OI.intelligence == [:]
  }

  @Test
  void should_reset_a_single_summaryName_after_capturing_entries() {
    OI.start("summaryName1")
    OI.start("summaryName2")

    OI.reset("summaryName1")

    assert !OI.intelligence["summaryName1"]
    assert OI.intelligence["summaryName2"]
  }

  @Test
  void should_allow_a_current_capture_to_continue_if_already_started() {
    OI.start("summaryName")
    OI.log("summaryName", "firstStart", "true")

    OI.start("summaryName")
    OI.log("summaryName", "secondStart", "true")

    def text = OI.stop("summaryName")
    
    assert text.contains("summary=\"summaryName\"")
    assert text.contains("firstStart=\"true\"")
    assert text.contains("secondStart=\"true\"")
  }

  @Test(expected = IllegalArgumentException)
  void should_fail_if_invalid_summaryName_passed_in_to_log() {
    OI.log("INVALID SUMMARY_NAME", "a", 1)
  }

  @Test(expected = IllegalArgumentException)
  void should_fail_if_invalid_summaryName_passed_in_to_logCounter() {
    OI.logCounter("INVALID SUMMARY_NAME", "counter")
  }

  @Test(expected = IllegalArgumentException)
  void should_fail_if_invalid_summaryName_passed_in_to_stop() {
    OI.stop("INVALID SUMMARY_NAME")
  }

  @Test
  void should_start_tracking_with_a_default_logger() {
    OI.start("summaryName")

    assert OI.isCapturing("summaryName")
  }

  @Test
  void should_capture_non_string_values() {
    OI.start("summaryName")
    OI.log("summaryName", "key", 123)
    def text = OI.stop("summaryName")

    assert text.contains("key=\"123\"")
  }

  @Test
  void should_collect_thread_independent_data() {

    def threads = (1..1).collect {
      Thread.start {
        def text
        def summaryName
        try {
          summaryName = Thread.currentThread().name

          OI.start(summaryName)
          OI.log(summaryName, "threadName", summaryName)
          10.times { OI.logCounter(summaryName, "counter") }
          text = OI.stop(summaryName)
        }
        catch (IllegalArgumentException e) {
          e.printStackTrace()
          fail("Should not throw this exception")
        }

        assert text, "capture failed, ${text} : ${summaryName}"
        assert text.contains("threadName=\"${summaryName}\"") 
        assert text.contains("counter=\"10\"") 
      }
    }
    
    threads.each { it.join() }
 }

  @Test
  void should_free_memory_associated_with_capture() {
    OI.start("summaryName")
    OI.log("summaryName", "key", "value")
    OI.stop("summaryName")

    assert OI.intelligence["summaryName"] == null
  }
}

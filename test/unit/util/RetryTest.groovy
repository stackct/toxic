package util

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import log.*

class RetryTest {

  @Rule
  public ExpectedException exception = ExpectedException.none()

  def callCount = 0

  @Before
  void before() {
    callCount = 0

    // Prevent accidental calls to thread sleep.
    Thread.metaClass.'static'.sleep = { long time -> throw new Exception("SHOULD HAVE MOCKED THIS") }
  }

  @After
  void after() {
    Thread.metaClass = null
  }

  @Test
  void should_return_result_of_closure() {
    assert "return this" == Retry.factory(FileNotFoundException).run() {
      "return this"
    }
  }

  @Test
  void should_return_result_of_closure_evan_after_retries() {
    Wait.mock() {
      assert "return this" == Retry.factory(FileNotFoundException, [maxTries: 2]).run() {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
        "return this"
      }
    }

    assert callCount == 2
  }

  @Test
  void should_return_null_if_operation_times_out() {
    Wait.mock() {
      assert null == Retry.factory(FileNotFoundException).run() {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
        "return this"
      }
    }

    assert callCount == 1
  }

  @Test
  void should_fail_if_there_is_no_closure() {
    exception.expect(IllegalArgumentException)
    exception.expectMessage("must provide a minimum of a closure to run")

    Retry.factory(FileNotFoundException).run()
  }

  @Test
  void should_mandate_an_expected_exception_that_is_not_Exception() {
    exception.expect(IllegalArgumentException)
    exception.expectMessage("do not use Exception. Instead be intentional about the exceptions you manage.")

    Retry.factory(Exception)
  }

  @Test
  void should_default_constructor() {
    assert Retry.factory(FileNotFoundException).maxTries == 1
  }

  @Test
  void should_initialize_name() {
    def text = Log.capture(Retry.log) {
      Wait.mock() {
        Retry.factory(FileNotFoundException, [ name: "log this" ]).run() {
          callCount++
          if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
        }
      }
    }

    assert text.contains("log this")
  }

  @Test
  void should_retry_after_specifc_time_period() {
    def sleepTime = 0

    Wait.mock(sleepAction: { time -> sleepTime = time }) {
      Retry.factory(FileNotFoundException, [ periodMs: 1234 ]).run() {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
      }
    }

    assert sleepTime == 1234
  }

  @Test
  void should_pause_between_retries() {
    def pauseCount = 0

    def startAction = { time -> 
      pauseCount++ 
    }

    Wait.mock(sleepAction: startAction) {
      assertRetry(FileNotFoundException, [ expectedCallCount: 2, maxTries: 2 ]) {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
      }
    }

    assert pauseCount == 1
  }

  @Test
  void should_not_pause_if_no_retry_required() {
    assertRetry(FileNotFoundException, [ expectedCallCount: 1, maxTries: 5 ]) {
      callCount++
    }
  }

  @Test
  void should_retry_a_number_of_times() {
    Wait.mock() {
      assertRetry(
        FileNotFoundException, [ expectedCallCount: 5, maxTries: 5 ]) {
        callCount++
        throw new FileNotFoundException("oh dear, file will never arrive")
      }
    }
  }

  @Test
  void should_not_retry_if_exception_is_not_expected() {
    exception.expect(FileNotFoundException)

    assertRetry(IllegalArgumentException, [ expectedCallCount: 1, maxTries: 2 ]) {
      callCount++
      if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
    }
  }

  @Test
  void should_only_retry_til_success() {
    Wait.mock() {
      assertRetry(FileNotFoundException, [ expectedCallCount: 1, maxTries: 100 ]) {
        callCount++
      }
    }
  }

  @Test
  void should_only_retry_til_success_after_some_failures() {
    Wait.mock() {
      assertRetry(FileNotFoundException, [ expectedCallCount: 2, maxTries: 100 ]) {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
      }
    }
  }

  @Test
  void should_retry_based_on_a_set_possible_exceptions() {
    Wait.mock() {
      assertRetry([FileNotFoundException, IllegalArgumentException], [ expectedCallCount: 3, maxTries: 100 ]) {
        callCount++
        if (callCount == 1) throw new FileNotFoundException("oh dear, didnt expect this")
        if (callCount == 2) throw new IllegalArgumentException("oh dear, didnt expect this")
      }
    }
  }

  private assertRetry(retryWhenThrown, options, closure) {
    callCount = 0

    def retrier = Retry.factory(retryWhenThrown, [maxTries: options.maxTries])
    retrier.name = options.name ?: "operation"
    retrier.run(closure) 

    assert callCount == options.expectedCallCount
  }
}


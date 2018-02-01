package util

import org.junit.*

import static junit.framework.TestCase.fail

public class WaitTest {

  @Before
  public void before() {
    Thread.metaClass = null
  }

  @After
  public void after() {
    Thread.metaClass = null
  }

  @Test
  void should_mock_the_sleep() {
    def mocked = false

    Wait.mock() {
      mocked = true
    }

    assert mocked
  }

  @Test
  void should_mock_the_sleep_with_action() {
    def mocked = false
    def slept = false

    Wait.mock(sleepAction: { slept = true }) {
      mocked = true

      Thread.sleep(0)
    }

    assert mocked
    assert slept
  }

  @Test
  void should_sleep_for_time() {
    def sleepCount = 0
    Thread.metaClass.'static'.sleep = { long time -> 
      sleepCount++
    }

    Wait.pause(10)

    assert sleepCount == 1
  }

  @Test
  public void should_construct_instance_with_condition() {
    assert Wait.on { -> } instanceof Wait
  }

  @Test
  public void should_wait_for_condition_retrying_based_on_interval() {
    Wait.on { -> false }.every(1).with { wait ->
      Thread.metaClass.'static'.sleep = { long time -> 
        assert time == 1; wait.codeBlock = { -> true }
      }
      wait.start()
    }
  }

  @Test(expected=TimeoutException)
  public void should_timeout_after_specified_time() {
    assert Wait.on { -> false }.every(1).until(5).start()
  }

  @Test
  public void should_interrupt_if_condition_is_met() {
    assert Wait.on { -> false }.every(1).unless { -> true }.start() == false
  }

  @Test
  public void should_abort_after_max_attempts() {
    def attempts = 0
    try {
      Wait.on { -> attempts++; false }.every(1).atMostAttempts(3).start()
      fail("Did not throw expected AttemptsExhaustedException")
    } catch(AttemptsExhaustedException e) {
      assert 3 == attempts
      assert 3 == e.attempts
    }
  }

  @Test(expected=TimeoutException)
  public void should_timeout_after_specified_time_before_attempts_exhausted() {
    assert Wait.on { -> Thread.sleep(5); false }.every(1).atMostMs(2).atMostAttempts(10).start()
  }

  @Test(expected=AttemptsExhaustedException)
  public void should_abort_on_attempts_before_specified_time() {
    assert Wait.on { -> false }.every(1).atMostMs(15000).atMostAttempts(2).start()
  }

  @Test
  public void should_execute_closure_before_retrying() {
    def pass=false
    Wait.on { -> pass}.every(1).atMostAttempts(3).beforeRetry {count -> pass = count >= 1}.start()
  }

  @Test
  public void should_pass_attempt_count_to_condition() {
    Wait.on { count -> count >= 2}.every(1).atMostAttempts(3).start()
  }

  @Test
  public void should_track_closure_results() {
    def successTest = { result -> result == 3}
    def waiter = Wait.on { attemptNum-> attemptNum }
            .every(1).atMostAttempts(3).forCondition(successTest)
    def waitReturn = waiter.start()
    assert waitReturn == true
    assert waiter.results == [1,2,3]
  }

  @Test
  public void should_track_last_closure_result() {
    def waiter = Wait.on { attemptNum->
      if(attemptNum < 3) {
        return false
      }
      return attemptNum
    }
            .every(1).atMostAttempts(3)
    def waitReturn = waiter.start()
    assert waitReturn == true
    assert waiter.lastResult == 3
  }

  @Test
  public void should_support_custom_success_conditions() {
    def successTest = { result -> result == 3}
    def waiter = Wait.on { attemptNum-> attemptNum }
    .every(1).atMostAttempts(3).forCondition(successTest)
    def waitReturn = waiter.start()
    assert waitReturn == true
    assert waiter.lastResult == 3
    assert waiter.results == [1,2,3]
  }

  @Test
  public void should_support_indefinitely_syntax() {
    def count =0
    Wait.indefinitely().forCondition { result -> count++; count == 2 }

    count = 0
    def stopWaiting = { -> count++; count == 2}
    Wait.indefinitely().unless(stopWaiting)
  }


}

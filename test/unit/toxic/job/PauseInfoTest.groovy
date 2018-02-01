package toxic.job

import org.junit.*
import groovy.mock.interceptor.*

public class PauseInfoTest {
  @Test
  void should_pause_unpause() {
    def pi = new PauseInfo("test")
    assert !pi.isPaused()
    pi.pause()
    assert pi.isPaused()
    pi.pause()
    assert pi.isPaused()
    pi.unpause()
    assert !pi.isPaused()
    pi.unpause()
    assert !pi.isPaused()
  }

  @Test
  void should_record_date() {
    def dt = new Date()
    def pi = new PauseInfo("test")
    def newDt = pi.getToggleDate() 
    assert newDt instanceof Date
    def newDt2 = pi.getToggleDate() 
    assert newDt == newDt2

    pi.pause()
    newDt2 = pi.getToggleDate()
    assert !newDt.is(newDt2)
  }
}


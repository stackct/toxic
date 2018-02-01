
package toxic

import log.Log
import org.apache.log4j.Level
import org.junit.*

public class TaskMasterTest {
  @Test
  public void testHaltOnError() {
    def props = new ToxicProperties()
    props.tmHaltOnError = 'true'
    props.tmReps = "10"
    props.tmRepPauseMillis = "1"
    props.tmTaskPauseMillis = "1"
    props.taskIterations = "10"
    
    def failure = new TaskResult('1', 'test', 'foo', 'bar')
    failure.setSuccess(false)
    
    def task = new Object() {
      def execute(def memory) {
        assert memory.tmResults instanceof List
        return [failure]
      }
    }
    
    def taskList = [task, task, task]
    def tm = new TaskMaster() {
      def initModule(String cp, def memory) {
        return taskList.iterator()
      }
    }
    
    tm.init(props, 'test')
    tm.call()

    assert tm.results.size() == 1
  }

  @Test
  public void testDoNotDiscardResults() {
    def props = new ToxicProperties()
    props.tmHaltOnError = 'true'
    props.tmReps = "10"
    props.tmRepPauseMillis = "1"
    props.tmTaskPauseMillis = "1"
    props.taskIterations = "10"

    def count = 0
    def task = new Object() {
      def execute(def memory) {
        def failure = new TaskResult("${count}", 'test', 'foo', 'bar')
        failure.setSuccess(true)
        return [failure]
      }
    }
    
    def taskList = [task, task, task]
    def tm = new TaskMaster() {
      def initModule(String cp, def memory) {
        return taskList.iterator()
      }
    }
    
    tm.init(props, 'test')
    assert tm.call()?.size() == 30
  }
  
  @Test
  public void testDiscardResults() {
    def props = new ToxicProperties()
    props.tmHaltOnError = 'true'
    props.tmReps = "10"
    props.tmRepPauseMillis = "1"
    props.tmTaskPauseMillis = "1"
    props.tmDiscardRepResults = "true"
    props.taskIterations = "10"

    def count = 0
    def task = new Object() {
      def execute(def memory) {
        def failure = new TaskResult("${count}", 'test', 'foo', 'bar')
        failure.setSuccess(true)
        return [failure]
      }
    }
    
    def taskList = [task, task, task]
    def tm = new TaskMaster() {
      def initModule(String cp, def memory) {
        return taskList.iterator()
      }
    }
    
    tm.init(props, 'test')
    assert tm.call()?.size() == 0
  }

  @Test
  public void testNoSleep() {
    def props = new ToxicProperties()
    props.tmReps = "2"
    props.tmRepPauseMillis = "1"

    def pauseTotal = 0
    def tm = new TaskMaster() {
      protected void doRep(int repNum) {
      }
      protected def pause(def delay) {
        pauseTotal += delay
      }
      protected void failIfNoResults(def results) {
      }
    }
    
    tm.init(props, 'test')
    assert tm.call()?.size() == 0
    assert pauseTotal == 0
  }

  @Test
  void should_log_error_during_caught_exception() {
    def tm = new TaskMaster()
    tm.metaClass.setup = {
      throw new RuntimeException('TaskMaster failed')
    }
    def mockLog = Log.getLogger(TaskMaster.class)
    tm.metaClass.getLog = { mockLog }
    mockLog.track { logger ->
      tm.call()
      assert logger.isLogged('TaskMaster failed', Level.ERROR)
    }
  }

  @Test
  void should_skip_task_masters_with_no_results_when_walking_results() {
    def actualResults = []
    def closure = { r -> actualResults << r }
    def taskMasterResultIndex = [:]

    def taskMasters = []
    taskMasters << new TaskMaster(results: [])
    taskMasters << new TaskMaster(results: [])
    taskMasters << new TaskMaster(results: [])
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 0 == actualResults.size()
  }

  @Test
  void should_only_walk_when_new_tasks_become_available() {
    def actualResults = []
    def closure = { r -> actualResults << r }
    def taskMasterResultIndex = [:]

    def taskMasters = []
    taskMasters << new TaskMaster(id: '1', results: [new TaskResult(id:'1.1'), new TaskResult(id:'1.2'), new TaskResult(id:'1.3')])
    taskMasters << new TaskMaster(id: '2', results: [new TaskResult(id:'2.1'), new TaskResult(id:'2.2')])
    taskMasters << new TaskMaster(id: '3', results: [new TaskResult(id:'3.1')])
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 6 == actualResults.size()

    // Re-walking does not create new results
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 6 == actualResults.size()

    // Changing already walked values does not create new results
    taskMasters[0].results[0].id = 'CHANGED'
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 6 == actualResults.size()

    // Adding a new TaskMaster will re-walk
    taskMasters << new TaskMaster(id: '4', results: [new TaskResult(id:'4.1')])
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 7 == actualResults.size()

    // Adding a new TaskResult to a TaskMaster will re-walk
    taskMasters[0].results << new TaskResult(id:'1.4')
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex, closure)
    assert 8 == actualResults.size()
  }

  @Test
  void should_fail_if_no_results() {
    def ta = new TaskMaster()
    def results = []
    ta.failIfNoResults(results)
    assert results.size() == 1
    assert results[0].success == false
  }

  @Test
  void should_not_force_fail_if_results() {
    def ta = new TaskMaster()
    def result = new TaskResult(id: 'test', family: 'test')
    result.success = true
    def results = [result]
    ta.failIfNoResults(results)
    assert results.size() == 1
    assert results[0].success == true
  }

  @Test
  void should_not_force_fail_if_discarding_results() {
    def ta = new TaskMaster()
    def props = new ToxicProperties()
    props.tmDiscardRepResults = "true"
    ta.init(props,"t1")

    def results = []
    ta.failIfNoResults(results)
    assert results.size() == 0
  }
}
// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.junit.Test

public class TaskTest {
  @Test
  public void testMergesNullReturnedTaskResults() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        return null
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([:])
    assert results != null
    assert results.size() == 1
  }


  @Test
  public void testMergesNonNullReturnedTaskResults() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        return [ new TaskResult("ret1", "family", "name", "type"),
                 new TaskResult("ret2", "family", "name", "type")]
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([:])
    assert results != null
    assert results.size() == 3
    assert results[1].id == "ret1"
    assert results[2].id == "ret2"
  }

  @Test
  public void testMergesIteratedReturnedTaskResults() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        return [ new TaskResult("ret${memory.taskIteration}-1", "family", "name", "type"),
                 new TaskResult("ret${memory.taskIteration}-2", "family", "name", "type")]
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([taskIterations:2])
    assert results != null
    assert results.size() == 5
    assert results[1].id == "ret0-1"
    assert results[2].id == "ret0-2"
    assert results[3].id == "ret1-1"
    assert results[4].id == "ret1-2"
  }


  @Test
  public void testFailedIterationHalts() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        if(memory.taskIteration == 2) {
          throw new RuntimeException("Force iteration 2 failure")
        }
        def taskResult = new TaskResult("ret${memory.taskIteration}-1", "family", "name", "type")
        taskResult.setSuccess(true)
        return [taskResult]
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([tmHaltOnError: 'true', taskIterations:10])
    assert results != null
    assert results.size() == 3
    assert results[1].id == "ret0-1"
    assert results[2].id == "ret1-1"
  }

  @Test
  public void testFailedIterationContinues() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        if(memory.taskIteration % 2 == 0) {
          throw new RuntimeException("Force iteration ${memory.taskIteration} failure")
        }
        def r = new TaskResult("ret${memory.taskIteration}-1", "family", "name", "type")
        r.setSuccess(true)
        return [r]
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([tmHaltOnError:'false', taskIterations:6])
    assert results != null
    assert results.size() == 7
    assert results[1].error?.toString()?.contains("iteration 0 failure")
    assert results[2].id == "ret1-1"
    assert results[3].error?.toString()?.contains("iteration 2 failure")
    assert results[4].id == "ret3-1"
    assert results[5].error?.toString()?.contains("iteration 4 failure")
    assert results[6].id == "ret5-1"

    results.each {
      assert it.startTime != null;
      assert it.stopTime != null;
    }
  }

  @Test
  public void testReportsOverallDuration() {
    def task = new Task() {
      @Override
      List<TaskResult> doTask(def Object memory) {
        Thread.sleep(500)
        def result = new TaskResult("result", "family", "name", "type")
        result.success = true
        return [result]
      }
    }
    def props = [:]
    task.init("", props)

    def results = task.execute([:])
    assert results != null
    assert results.size() == 2
    assert results[0].duration > 500
  }

}
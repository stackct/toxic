package toxic

import org.junit.Test

class MissingFileTaskTest {

  @Test
  void should_return_a_failed_task_result() {
    def fixture = new MissingFileTask()
    def props = new ToxicProperties()
    fixture.init(new File("bogusFile"), props)
    def results = fixture.doTask([taskId: 'someTask'])
    assert results.size() == 1
    def result = results[0]
    assert !result.success
    assert result.error.toString().indexOf("bogusFile") >= 0
  }
}

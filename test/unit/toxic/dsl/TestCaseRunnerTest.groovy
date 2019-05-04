package toxic.dsl

import org.junit.Test
import toxic.TaskMaster
import toxic.ToxicProperties
import toxic.dir.DirItem

class TestCaseRunnerTest {
  private TaskMaster taskMaster
  private TestCase testCase
  private TestCaseRunner testCaseRunner
  private ConfigObject props

  TestCaseRunnerTest() {
    taskMaster = new TaskMaster()
    testCase = new TestCase(name: 'test')
    props = new ToxicProperties()
    testCaseRunner = new TestCaseRunner(taskMaster, testCase, props)
  }

  @Test
  void should_run_test_cases() {
    props.functions = ['fn': new Function(path: '/path/to/fn')]
    testCase.stepSequence = [[step: new Step(function: 'fn')]]

    def dirItems = []
    testCaseRunner.metaClass.executeDirItem = { DirItem dirItem ->
      dirItems << dirItem
    }
    testCaseRunner.call()
    assert null == testCaseRunner.error
    assert 2 == dirItems.size()
    assert '/path/to/fn' == dirItems[0].file.absolutePath
    assert 'noop' == dirItems[1].file.name
  }
}

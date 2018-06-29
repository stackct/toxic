package toxic.dsl

import org.junit.After
import org.junit.Test

class StepFileTest {
  @Test
  void should_construct() {
    Step parentStep = new Step()
    StepFile stepFile = new StepFile('foo', parentStep, true)
    assert 'foo' == stepFile.name
    assert parentStep == stepFile.parentStep
    assert true == stepFile.lastChildStep
  }

  @Test
  void should_complete_step() {
    Step step = new Step(name: 'fooStep', function: 'fooFn')
    def props = [stepIndex: 0, testCases: [new TestCase(steps: [step])], functions: ['fooFn':new Function(outputs: ['foo': 'bar'])], backup: [:]]
    new StepFile('foo', null, false).complete(props)
    assert 1 == props.stepIndex
    assert 'bar' == step.outputs.foo
  }

  @Test
  void should_complete_parent_step() {
    Step fooStep = new Step(name: 'fooStep', function: 'fooFn')
    Function fooFn = new Function(outputs: ['foo1': 'bar1'])
    Step barStep = new Step(name: 'barStep', function: 'barFn')
    Function barFn = new Function(outputs: ['foo2': 'bar2'])
    def props = [stepIndex: 0, testCases: [new TestCase(steps: [fooStep])], functions: ['fooFn':fooFn, 'barFn': barFn], backup: [:]]
    new StepFile('foo', barStep, true).complete(props)
    assert 1 == props.stepIndex
    assert 'bar1' == fooStep.outputs.foo1
    assert 'bar2' == barStep.outputs.foo2
  }
}

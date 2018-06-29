package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class StepOutputResolverTest {
  @Test
  void should_resolve_missing_property() {
    Step step = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(steps: [step])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn': new Function()]]
    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('someStep')
  }

  @Test
  void should_fail_to_resolve_missing_property_when_step_is_not_found() {
    Step step = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(name: 'someTestCase', steps: [step])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn': new Function()]]

    try {
      new StepOutputResolver(props).propertyMissing('invalidStep')
      fail('Expected Exception')
    }
    catch(StepNotFoundException e) {
      assert 'Could not resolve values due to undefined step; testCase=someTestCase; step=invalidStep' == e.message
    }
  }

  @Test
  void should_resolve_step_outputs_from_function() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'barStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1': new Function(steps: [barStep]), 'someFn2': new Function()]]
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('barStep')
  }

  @Test
  void should_resolve_last_step_output_when_step_names_are_duplicated() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep, barStep])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(), 'someFn2':new Function()]]
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }

  @Test
  void should_resolve_last_step_output_in_function_step_when_step_names_are_duplicated() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(steps: [barStep]), 'someFn2':new Function()]]
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }

  @Test
  void should_resolve_last_step_output_in_second_step_when_step_names_are_duplicated() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    Step foobarStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k3':'v3'])
    TestCase testCase = new TestCase(steps: [fooStep, foobarStep])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(steps: [barStep]), 'someFn2':new Function()]]
    assert ['k3':'v3'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }
}

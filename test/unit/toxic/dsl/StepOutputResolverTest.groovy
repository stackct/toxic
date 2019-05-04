package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class StepOutputResolverTest {
  @Test
  void should_resolve_missing_property() {
    Step fooStep = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep, barStep], stepSequence: [])
    testCase.stepSequence << [step: fooStep, level: 0]
    testCase.stepSequence << [step: barStep, level: 0]

    def props = [testCase:testCase, stepIndex: 0, functions: ['someFn': new Function()]]
    props.stepIndex++
    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('someStep')
  }

  @Test
  void should_fail_to_resolve_missing_property_when_step_is_not_found() {
    Step step = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(name: 'someTestCase', steps: [step], stepSequence: [])
    testCase.stepSequence << [step: step, level: 0]

    def props = [testCase:testCase, stepIndex: 0, functions: ['someFn': new Function()]]

    try {
      new StepOutputResolver(props).propertyMissing('invalidStep')
      fail('Expected Exception')
    }
    catch(StepNotFoundException e) {
      assert 'Could not resolve values due to undefined step; testCase=someTestCase; step=invalidStep; stepIndex=0; stepLevel=0' == e.message
    }
  }

  @Test
  void should_resolve_step_outputs_from_function() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'barStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep], stepSequence: [])
    testCase.stepSequence << [step: fooStep, level: 0]
    testCase.stepSequence << [step: barStep, level: 1]

    def props = [testCase:testCase, stepIndex: 0, functions: ['someFn1': new Function(steps: [barStep]), 'someFn2': new Function()], stepSequence: []]

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('barStep')
  }
}

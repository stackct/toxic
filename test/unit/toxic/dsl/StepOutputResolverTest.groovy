package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class StepOutputResolverTest {
  @Test
  void should_resolve_missing_property() {
    Step fooStep = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep, barStep])

    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn': new Function()], stepSequence: []]
    props.stepSequence << [step: fooStep, level: 0]
    props.stepSequence << [step: barStep, level: 0]

    props.stepIndex++
    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('someStep')
  }

  @Test
  void should_fail_to_resolve_missing_property_when_step_is_not_found() {
    Step step = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(name: 'someTestCase', steps: [step])
    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn': new Function()], stepSequence: []]
    props.stepSequence << [step: step, level: 0]

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
    TestCase testCase = new TestCase(steps: [fooStep])

    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1': new Function(steps: [barStep]), 'someFn2': new Function()], stepSequence: []]
    props.stepSequence << [step: fooStep, level: 0]
    props.stepSequence << [step: barStep, level: 1]

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('barStep')
  }

  @Test
  void should_resolve_step_output_when_step_names_are_duplicated() {
    Step fooStep = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step barStep = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(steps: [fooStep, barStep])

    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(), 'someFn2':new Function()], stepSequence: []]
    props.stepSequence << [step: fooStep, level: 0]
    props.stepSequence << [step: barStep, level: 0]

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }

  @Test
  void should_resolve_step_output_in_function_when_step_names_are_duplicated() {
    Step fooStep1 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step fooStep2 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    Step fooStep3 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k3':'v3'])
    TestCase testCase = new TestCase(steps: [fooStep1])

    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(steps: [fooStep2, fooStep3]), 'someFn2':new Function()], stepSequence: []]
    props.stepSequence << [step: fooStep1, level: 0]
    props.stepSequence << [step: fooStep2, level: 1]
    props.stepSequence << [step: fooStep3, level: 1]

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k3':'v3'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }

  @Test
  void should_resolve_output_in_second_step_when_step_names_are_duplicated() {
    Step fooStep1 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step fooStep2 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    Step fooStep3 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k3':'v3'])
    Step fooStep4 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k4':'v4'])
    Step fooStep5 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k5':'v5'])
    TestCase testCase = new TestCase(steps: [fooStep1, fooStep3, fooStep4, fooStep5])

    def props = [testCases:[testCase], stepIndex: 0, functions: ['someFn1':new Function(steps: [fooStep2]), 'someFn2':new Function()], stepSequence: []]
    props.stepSequence << [step: fooStep1, level: 0]
    props.stepSequence << [step: fooStep2, level: 1]
    props.stepSequence << [step: fooStep3, level: 0]
    props.stepSequence << [step: fooStep4, level: 0]
    props.stepSequence << [step: fooStep5, level: 0]

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k3':'v3'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k4':'v4'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k5':'v5'] == new StepOutputResolver(props).propertyMissing('fooStep')
  }

  @Test
  void should_resolve_output_in_second_test_case_when_step_names_are_duplicated() {
    Step fooStep1 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k1':'v1'])
    Step fooStep2 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k2':'v2'])
    Step fooStep3 = new Step(name: 'fooStep', function: 'someFn1')
    TestCase testCase1 = new TestCase(steps: [fooStep1, fooStep2, fooStep3])
    Step fooStep4 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k3':'v3'])
    Step fooStep5 = new Step(name: 'fooStep', function: 'someFn1', outputs: ['k4':'v4'])
    Step fooStep6 = new Step(name: 'fooStep', function: 'someFn1')
    TestCase testCase2 = new TestCase(steps: [fooStep4, fooStep5, fooStep6])

    def props = [testCases:[testCase1, testCase2], stepIndex: 0, functions: ['someFn1':new Function()], stepSequence: []]
    testCase1.steps.each {
      props.stepSequence << [step: it, level: 0]
    }
    testCase2.steps.each {
      props.stepSequence << [step: it, level: 0]
    }

    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k2':'v2'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert [:] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k3':'v3'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert ['k4':'v4'] == new StepOutputResolver(props).propertyMissing('fooStep')
    props.stepIndex++
    assert [:] == new StepOutputResolver(props).propertyMissing('fooStep')
  }
}

package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class StepOutputResolverTest {
  @Test
  void should_resolve_missing_property() {
    def step1 = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    def step2 = new Step(name: 'fooStep', function: 'someFn2', outputs: ['k2':'v2'])
    TestCase testCase = new TestCase(name: 'foo')

    assert ['k1':'v1'] == new StepOutputResolver(testCase, step2, [step1, step2]).propertyMissing('someStep')
  }

  @Test
  void should_fail_to_resolve_missing_property_when_step_is_not_found() {
    Step step = new Step(name: 'someStep', function: 'someFn', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(name: 'someTestCase')

    try {
      new StepOutputResolver(testCase, step, [step]).propertyMissing('invalidStep')
      fail('Expected Exception')
    }
    catch(StepNotFoundException e) {
      assert 'Could not resolve values due to undefined step; testCase=someTestCase; step=someStep; missingStepReference=step.invalidStep' == e.message
    }
  }
}

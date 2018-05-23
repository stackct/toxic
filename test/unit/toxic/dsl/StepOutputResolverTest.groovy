package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class StepOutputResolverTest {
  @Test
  void should_resolve_missing_property() {
    Step step = new Step(name: 'someStep', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(steps: [step])
    def props = [testCases:[testCase], stepIndex: 0]
    assert ['k1':'v1'] == new StepOutputResolver(props).propertyMissing('someStep')
  }

  @Test
  void should_fail_to_resolve_missing_property_when_step_is_not_found() {
    Step step = new Step(name: 'someStep', outputs: ['k1':'v1'])
    TestCase testCase = new TestCase(name: 'someTestCase', steps: [step])
    def props = [testCases:[testCase], stepIndex: 0]

    try {
      new StepOutputResolver(props).propertyMissing('invalidStep')
      fail('Expected Exception')
    }
    catch(StepNotFoundException e) {
      assert 'Could not resolve values due to undefined step; testCase=someTestCase; step=invalidStep' == e.message
    }
  }
}

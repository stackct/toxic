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

  @Test
  void should_find_current_test_case() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(name: 'tc1', steps: testCase1Steps), new TestCase(name: 'tc2', steps: testCase2Steps)]

    new StepOutputResolver().with {
      assert 'tc1' == it.currentTestCase(testCases, 0).name
      assert 'tc1' == it.currentTestCase(testCases, 1).name
      assert 'tc1' == it.currentTestCase(testCases, 2).name
      assert 'tc2' == it.currentTestCase(testCases, 3).name
      assert 'tc2' == it.currentTestCase(testCases, 4).name
      assert 'tc2' == it.currentTestCase(testCases, 5).name
      assert null == it.currentTestCase(testCases, 6)
    }
  }
}

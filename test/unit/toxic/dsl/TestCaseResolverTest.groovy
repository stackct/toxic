package toxic.dsl

import org.junit.Test
import toxic.ToxicProperties

import static org.junit.Assert.fail

class TestCaseResolverTest {
  @Test
  void should_find_current_test_case() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(name: 'tc1', steps: testCase1Steps), new TestCase(name: 'tc2', steps: testCase2Steps)]

    ToxicProperties props = new ToxicProperties()
    props.testCases = testCases
    props.stepIndex = 0

    new TestCaseResolver(){}.with {
      assert 'tc1' == it.currentTestCase(props).name
      props.stepIndex++
      assert 'tc1' == it.currentTestCase(props).name
      props.stepIndex++
      assert 'tc2' == it.currentTestCase(props).name
      props.stepIndex++
      assert 'tc2' == it.currentTestCase(props).name
      props.stepIndex++
      assert 'tc2' == it.currentTestCase(props).name
      props.stepIndex++
      assert null == it.currentTestCase(props)
    }
  }
}

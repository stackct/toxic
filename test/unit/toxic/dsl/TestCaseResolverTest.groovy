package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class TestCaseResolverTest {
  @Test
  void should_find_current_test_case() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(name: 'tc1', steps: testCase1Steps), new TestCase(name: 'tc2', steps: testCase2Steps)]

    new TestCaseResolver(){}.with {
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

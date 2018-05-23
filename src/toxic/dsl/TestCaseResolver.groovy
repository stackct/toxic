package toxic.dsl

abstract class TestCaseResolver {

  abstract def propertyMissing(String name);

  TestCase currentTestCase(List<TestCase> testCases, int stepIndex) {
    int totalTestCaseExecutions = 0
    testCases.find { testCase ->
      totalTestCaseExecutions += testCase.steps.size()
      // Using <= because the assertion step needs to be considered within the current TestCase
      stepIndex <= totalTestCaseExecutions
    }
  }
}

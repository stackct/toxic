package toxic.dsl

abstract class TestCaseResolver {

  abstract def propertyMissing(String name);

  TestCase currentTestCase(props) {
    int totalTestCaseExecutions = 0
    props.testCases.find { testCase ->
      totalTestCaseExecutions += TestCaseHandler.flattenTestCaseSteps(testCase.steps, props).size()
      props.stepIndex < totalTestCaseExecutions
    }
  }
}

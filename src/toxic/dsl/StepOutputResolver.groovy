package toxic.dsl

class StepOutputResolver {
  def props

  StepOutputResolver(def props) {
    this.props = props
  }

  def propertyMissing(String name)  {
    TestCase testCase = currentTestCase(props.testCases, props.stepIndex)
    Step step = testCase.steps.find { it.name == name }
    if(!step) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${name}")
    }
    step.outputs
  }

  TestCase currentTestCase(List<TestCase> testCases, int stepIndex) {
    int totalTestCaseExecutions = 0
    testCases.find { testCase ->
      totalTestCaseExecutions += testCase.steps.size()
      // Using <= because the assertion step needs to be considered within the current TestCase
      stepIndex <= totalTestCaseExecutions
    }
  }
}

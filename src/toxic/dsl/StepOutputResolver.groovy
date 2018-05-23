package toxic.dsl

class StepOutputResolver extends TestCaseResolver {
  def props

  StepOutputResolver(def props) {
    this.props = props
  }

  @Override
  def propertyMissing(String name)  {
    TestCase testCase = currentTestCase(props.testCases, props.stepIndex)
    Step step = testCase.steps.find { it.name == name }
    if(!step) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${name}")
    }
    step.outputs
  }
}

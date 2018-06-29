package toxic.dsl

class StepOutputResolver extends TestCaseResolver {
  def props

  StepOutputResolver(def props) {
    this.props = props
  }

  @Override
  def propertyMissing(String name)  {
    TestCase testCase = currentTestCase(props)
    Step step = findLatestStepByName(name, testCase.steps)
    if(!step) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${name}")
    }
    step.outputs
  }

  private Step findLatestStepByName(String name, List<Step> steps) {
    Step foundStep
    steps?.each { step ->
      if(step.name == name) {
        foundStep = step
      }
      Function function = props.functions["${step.function}"]
      Step foundSubStep = findLatestStepByName(name, function?.steps)
      if(foundSubStep) {
        foundStep = foundSubStep
      }
    }
    return foundStep
  }
}

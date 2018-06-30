package toxic.dsl

class StepOutputResolver {
  def props

  StepOutputResolver(def props) {
    this.props = props
  }

  def propertyMissing(String name)  {
    TestCase testCase = TestCaseHandler.currentTestCase(props)
    Step resolvedStep
    int currentStepLevel = props.stepSequence[props.stepIndex].level
    props.stepSequence.eachWithIndex { sequence, index ->
      Step step = sequence.step
      // Using <= so when the last child step completes, the output interpolation on the parent will resolve correctly
      if(index <= props.stepIndex && sequence.level == currentStepLevel && step.name == name) {
        resolvedStep = step
      }
    }
    if(!resolvedStep) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${name}; stepIndex=${props.stepIndex}; stepLevel=${currentStepLevel}")
    }
    resolvedStep.outputs
  }
}

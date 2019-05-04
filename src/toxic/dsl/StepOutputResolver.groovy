package toxic.dsl

import log.Log

class StepOutputResolver {
  private final static Log log = Log.getLogger(this)
  def props

  StepOutputResolver(def props) {
    this.props = props
  }

  def propertyMissing(String name)  {
    TestCase testCase = props.testCase
    Step resolvedStep
    int currentStepLevel = testCase.stepSequence[props.stepIndex].level
    testCase.stepSequence.eachWithIndex { sequence, index ->
      Step step = sequence.step
      if(index <= props.stepIndex && sequence.level == currentStepLevel && step.name == name) {
        log.debug("Resolving output from step; index=${index}; stepIndex=${props.stepIndex}; level=${sequence.level}; currentStepLevel=${currentStepLevel}; test=${testCase.name}; step=${step.name}; fn=${step.function}")
        resolvedStep = step
      }
    }
    if(!resolvedStep) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${name}; stepIndex=${props.stepIndex}; stepLevel=${currentStepLevel}")
    }
    resolvedStep.outputs
  }
}

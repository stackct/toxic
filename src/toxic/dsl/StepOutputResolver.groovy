package toxic.dsl

class StepOutputResolver {
  TestCase testCase
  Step step
  List<Step> scopedSteps

  StepOutputResolver(TestCase testCase, Step step, List<Step> scopedSteps) {
    this.testCase = testCase
    this.step = step
    this.scopedSteps = scopedSteps
  }

  def propertyMissing(String name)  {
    Step resolvedStep = scopedSteps.find {
      it.name == name
    }
    if(!resolvedStep) {
      throw new StepNotFoundException("Could not resolve values due to undefined step; testCase=${testCase.name}; step=${step.name}; missingStepReference=step.${name}")
    }
    resolvedStep.outputs
  }
}

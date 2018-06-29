package toxic.dsl

class StepFile extends File {
  Step parentStep
  boolean lastChildStep

  StepFile(String name, Step parentStep = null, boolean lastChildStep = false) {
    super(name)
    this.parentStep = parentStep
    this.lastChildStep = lastChildStep
  }

  void complete(props) {
    TestCaseHandler.completeCurrentStep(props)
    if(lastChildStep) {
      TestCaseHandler.completeStep(parentStep, props)
    }
    TestCaseHandler.startNextStep(props)
  }
}

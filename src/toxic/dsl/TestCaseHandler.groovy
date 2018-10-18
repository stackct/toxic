package toxic.dsl

import log.Log
import toxic.dir.DirItem
import toxic.dir.LinkHandler

class TestCaseHandler extends LinkHandler {
  private final static Log log = Log.getLogger(this)

  TestCaseHandler(DirItem item, def props) {
    super(item, props)
  }

  void lazyInit(File file) {
    props.stepIndex = 0
    props.stepSequence = []
    props.testCases = TestCase.parse(file.text).findAll { shouldInclude(it) }
    props.step = new StepOutputResolver(props)
    props.var = new VariableResolver(props)

    props.testCases.each { testCase ->
      addSteps(testCase.steps)
      Closure resolver = { contents ->
        def interpolatedContents = ''<<''
        contents.eachLine { interpolatedContents << Step.interpolate(props, it) + '\n' }
        interpolatedContents.toString()
      }
      addChild(new DirItem(testCase.assertionFile(file, resolver), item))
      Step assertionStep = new AssertionStep()
      testCase.steps << assertionStep
      props.stepSequence << [step: assertionStep, level: 0]
    }
  }

  void addSteps(List<Step> steps, Step parentStep=null, List<String> functionCallStack = []) {
    steps.eachWithIndex { step, index ->
      step.inheritPrefix(parentStep)

      if(!props.functions.containsKey(step.function)) {
        throw new IllegalStateException("Undefined function; name=${step.function}")
      }
      props.stepSequence << [step: step, level: functionCallStack.size()]
      Function function = fromStep(step, props)
      if(function.path) {
        boolean lastChildStep = (parentStep && steps.size()-1 == index)
        addChild(new DirItem(new StepFile("${function.path}", parentStep, lastChildStep), item))
      }
      else if(functionCallStack.contains(function.name)) {
        throw new IllegalStateException("Circular function call detected; name=${function.name}; callStack=${functionCallStack}")
      }
      else {
        List<String> stepsFunctionCallStack = functionCallStack.collect()
        stepsFunctionCallStack << function.name
        addSteps(function.steps, step, stepsFunctionCallStack)
      }
    }
  }

  boolean shouldInclude(TestCase testCase) {
    if (props.test) {
      return testCase.name == props.test
    }

    boolean include = true

    if (props.includeTags) {
      include &= hasAny(testCase, props.includeTags.tokenize(','))
    }

    if (props.excludeTags) {
      include &= !hasAny(testCase, props.excludeTags.tokenize(','))
    }

    return include
  }

  private boolean hasAny(TestCase testCase, List tags) {
    tags.any { t -> testCase.tags.contains(t) }
  }

  @Override
  File nextFile(File f) {
    if (!item.children) {
      lazyInit(f)
      startStep(props)
    }
    item.nextChild(props)
  }

  static void startStep(props) {
    Step step = currentStep(props)
    if(step) {
      TestCase testCase = currentTestCase(props)
      if(step instanceof AssertionStep) {
        log.info("Executing test assertions; test=\"${testCase.name}\"")
      }
      else {
        Function function = fromStep(step, props)
        String fnDetails = function.path ? "fnPath=${function.path}" : "subSteps=${function.steps.size()}"
        log.info("Executing step; test=\"${testCase.name}\"; name=${step.name}; fnName=${function.name}; ${fnDetails}")
        props.push()
        copyStepArgsToMemory(props)
        if(!function.path) {
          startNextStep(props)
        }
      }
    }
  }

  static void startNextStep(props) {
    props.stepIndex++
    startStep(props)
  }

  static void completeCurrentStep(props) {
    completeStep(currentStep(props), props)
  }

  static void completeStep(Step step, props) {
    log.debug("Completing step; name=${step.name}")
    moveOutputResultsToStep(step, props)
    int stepIndex = props.stepIndex
    props.pop()
    props.stepIndex = stepIndex
  }

  static void copyStepArgsToMemory(props) {
    Step step = currentStep(props)
    Function function = fromStep(step, props)
    function.validateRequiredArgsArePresent(step.args)
    function.args.each { arg ->
      if(arg.hasDefaultValue && !step.args.containsKey(arg.name)) {
        step.args[arg.name] = arg.defaultValue
      }
    }
    step.args.each { k, v ->
      function.validateArgIsDefined(k)
      def interpolatedValue = Step.interpolate(props, v)
      log.debug("Copying step input to memory; test=${currentTestCase(props).name}; step=${step.function}; ${k}=${interpolatedValue}")
      props[k] = interpolatedValue
    }
  }

  static void moveOutputResultsToStep(Step step, props) {
    Function function = fromStep(step, props)
    if(function) {
      function.outputs.each { k, v ->
        step.outputs[k] = v ? Step.interpolate(props, v) : props[k]
      }
    }
    else {
      log.debug("Skipping output result copy because function was not found for step; step=${step.name}")
    }
  }

  static TestCase currentTestCase(props) {
    int totalTestCaseExecutions = 0
    props.testCases.find { testCase ->
      totalTestCaseExecutions += flattenTestCaseSteps(testCase.steps, props).size()
      props.stepIndex < totalTestCaseExecutions
    }
  }

  static Step currentStep(props) {
    Step step
    int stepIndex = props.stepIndex
    int totalTestCaseExecutions = 0
    props.testCases.each { testCase ->
      List<Step> testCaseSteps = flattenTestCaseSteps(testCase.steps, props)
      int totalTestCaseSteps = testCaseSteps.size()
      totalTestCaseExecutions += totalTestCaseSteps
      if(!step && stepIndex < totalTestCaseExecutions) {
        int foundIndex = stepIndex - (totalTestCaseExecutions - totalTestCaseSteps)
        if(foundIndex >= 0) {
          step = testCaseSteps[foundIndex]
        }
      }
    }
    step
  }

  static Function fromStep(Step step, props) {
    props.functions["${step.function}"]
  }

  static List<Step> flattenTestCaseSteps(List<Step> steps, props) {
    List<Step> flattenedSteps = []
    steps?.each { step ->
      flattenedSteps << step
      Function function = fromStep(step, props)
      flattenedSteps += flattenTestCaseSteps(function?.steps, props)
    }
    return flattenedSteps
  }
}

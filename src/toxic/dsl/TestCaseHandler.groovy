package toxic.dsl

import toxic.dir.DirItem
import toxic.dir.LinkHandler

import org.apache.log4j.Logger

class TestCaseHandler extends LinkHandler {
  protected static Logger log = Logger.getLogger(this)

  TestCaseHandler(DirItem item, def props) {
    super(item, props)
  }

  void lazyInit(File file) {
    props.stepIndex = 0
    props.testCases = TestCase.parse(file.text).findAll { shouldInclude(it) }
    props.step = new StepOutputResolver(props)
    props.backup = props.clone()

    props.testCases.each { testCase ->
      testCase.steps.each { step ->
        if(!props.functions.containsKey(step.function)) {
          throw new IllegalStateException("Undefined function; name=${step.function}")
        }
        Function function = props.functions["${step.function}"]
        addChild(new DirItem(new StepFile("${function.path}"), item))
      }
      Closure resolver = { contents ->
        def interpolatedContents = ''<<''
        contents.eachLine { interpolatedContents << Step.interpolate(props, it) + '\n' }
        interpolatedContents.toString()
      }
      addChild(new DirItem(testCase.assertionFile(file, resolver), item))
    }
  }

  boolean shouldInclude(TestCase testCase) {
    if (!props.tags) return true

    props.tags.tokenize(',').any { f -> testCase.tags.contains(f) }
  }

  @Override
  File nextFile(File f) {
    if (!item.children) {
      lazyInit(f)
      copyStepArgsToMemory(props)
    }
    item.nextChild(props)
  }

  static void stepComplete(props) {
    removeStepArgsFromMemory(props)
    moveOutputResultsToStep(props)
    props.stepIndex++
    copyStepArgsToMemory(props)
  }

  static void copyStepArgsToMemory(props) {
    Step step = currentStep(props.testCases, props.stepIndex)
    if(step) {
      Function function = props.functions["${step.function}"]
      function.validateRequiredArgsArePresent(step.args)
      step.args.each { k, v ->
        function.validateArgIsDefined(k)
        setWithBackup(k, Step.interpolate(props, v), props, props.backup)
      }
    }
  }

  static void removeStepArgsFromMemory(props) {
    Step step = currentStep(props.testCases, props.stepIndex)
    step?.args?.keySet().each {
      removeWithRestore(it, props, props.backup)
    }
  }

  static void moveOutputResultsToStep(props) {
    Step step = currentStep(props.testCases, props.stepIndex)
    if(step) {
      props.functions["${step.function}"].outputs.each {
        step.outputs[it] = props[it]
        removeWithRestore(it, props, props.backup)
      }
    }
  }

  static Step currentStep(List<TestCase> testCases, int stepIndex) {
    Step step
    int totalTestCaseExecutions = 0
    testCases.each { testCase ->
      totalTestCaseExecutions += testCase.steps.size()
      if(!step && stepIndex < totalTestCaseExecutions) {
        int foundIndex = stepIndex - (totalTestCaseExecutions - testCase.steps.size())
        if(foundIndex >= 0) {
          step = testCase.steps[foundIndex]
        }
      }
    }
    step
  }

  private static void setWithBackup(key, newVal, props, backup) {
    if (props[key]) {
      backup[key] = props[key]
    }
    props[key] = newVal
  }

  private static void removeWithRestore(key, props, backup) {
    if (backup[key]) {
      props[key] = backup[key]
      backup.remove(key)
    } else {
      props.remove(key)
    }
  }
}

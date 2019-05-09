package toxic.dsl

import toxic.TaskResult

class TestCaseTask extends toxic.Task {
  @Override
  List<TaskResult> doTask(def props) {
    if (!(input instanceof File) ) {
      return null
    }

    def testCases = parse(input.text)

    if(isInDir(props.setupDir)) {
      addTestCases('setupTestCases', testCases)
    }
    else if(isInDir(props.teardownDir)) {
      addTestCases('teardownTestCases', testCases)
    }
    else {
      addTestCases('testCases', testCases)
    }

    return null
  }

  boolean isInDir(def dir) {
    return dir && input.absolutePath.startsWith(dir)
  }

  void addTestCases(String key, List<TestCase> testCases) {
    props["${key}"] = (props["${key}"] ?: []) + testCases
  }

  List<TestCase> parse(String input) {
    def testCases = TestCase.parse(input).findAll { shouldInclude(it) }
    testCases.each { testCase ->
      testCase.file = this.input
      addSteps(testCase, testCase.steps)
    }
    return testCases
  }

  void addSteps(TestCase testCase, List<Step> steps, Step parentStep=null, List<String> functionCallStack = []) {
    steps.eachWithIndex { step, index ->
      step.inheritPrefix(parentStep)

      if(!props.functions.containsKey(step.function)) {
        throw new IllegalStateException("Undefined function; name=${step.function}")
      }
      testCase.stepSequence << [step: step, level: functionCallStack.size()]
      Function function = step.getFunction(props)
      if(function.path) {
        step.parentStep = parentStep
        step.lastStepInSequence = (parentStep && steps.size()-1 == index)
      }
      else if(functionCallStack.contains(function.name)) {
        throw new IllegalStateException("Circular function call detected; name=${function.name}; callStack=${functionCallStack}")
      }
      else {
        List<String> stepsFunctionCallStack = functionCallStack.collect()
        stepsFunctionCallStack << function.name
        addSteps(testCase, cloneSteps(function.steps), step, stepsFunctionCallStack)
      }
    }
  }

  List<Step> cloneSteps(List<Step> steps) {
    steps.collect {
      def bos = new ByteArrayOutputStream()
      def oos = new ObjectOutputStream(bos)
      oos.writeObject(it);
      oos.flush()
      def bin = new ByteArrayInputStream(bos.toByteArray())
      def ois = new ObjectInputStream(bin)
      return ois.readObject()
    }
  }

  boolean shouldInclude(TestCase testCase) {
    if (props.test) {
      return testCase.name == props.test
    }

    boolean include = true
    if (props.includeTags) {
      include &= isTagFound(testCase.tags, props.includeTags)
    }
    if (props.excludeTags) {
      include &= !isTagFound(testCase.tags, props.excludeTags)
    }
    return include
  }

  boolean isTagFound(Set tags, def testTagStr) {
    boolean found = false
    if (testTagStr) {
      def testTags = testTagStr.toString().tokenize(',')
      found = testTags.any { t -> tags.contains(t) }
    }
    return found
  }
}

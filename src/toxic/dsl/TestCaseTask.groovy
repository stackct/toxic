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
      Step.parseSteps(testCase.steps, props)
    }
    return testCases
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

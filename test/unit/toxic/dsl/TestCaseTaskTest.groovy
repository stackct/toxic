package toxic.dsl

import org.junit.Test
import toxic.ToxicProperties
import static org.junit.Assert.fail

import static org.junit.Assert.fail

class TestCaseTaskTest {
  private ConfigObject props
  private TestCaseTask testCaseTask

  TestCaseTaskTest() {
    props = new ToxicProperties()
    testCaseTask = new TestCaseTask(props: props)
  }

  @Test
  void should_not_parse_string_input() {
    testCaseTask.input = "foo"
    assert null == testCaseTask.doTask(props)
  }

  @Test
  void should_parse_test_directory() {
    def input = """
      test "test1" {
        description "test1 description"

        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    def functions = ['fn_1': new Function(path: 'fn_1')]

    File tempDir
    try {
      tempDir = File.createTempDir()
      def file = new File(tempDir, 'foo.test')
      file.text = input

      def props = [setupDir:tempDir.absolutePath, functions: functions]
      assert null == new TestCaseTask(props: props, input: file).doTask(props)
      assert 1 == props.setupTestCases.size()

      props = [teardownDir:tempDir.absolutePath, functions: functions]
      assert null == new TestCaseTask(props: props, input: file).doTask(props)
      assert 1 == props.teardownTestCases.size()

      props = [functions: functions]
      assert null == new TestCaseTask(props: props, input: file).doTask(props)
      assert 1 == props.testCases.size()
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_parse_test_case_file() {
    def input = """
      test "test1" {
        description "test1 description"

        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test2" {
        description "test2 description"

        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }

        step "fn_2", "step2", {
            arg1 1
            arg2 2
        }

        step "fn_3", "step3", {
            arg1 1
            arg2 2
        }
      }
    """

    props.functions = ['fn_1': new Function(path: 'fn_1'), 'fn_2': new Function(path: 'fn_2'), 'fn_3': new Function(path: 'fn_3')]
    def testCases = testCaseTask.parse(input)

    assert 2 == testCases.size()

    assert 1 == testCases[0].steps.size()
    assert 'step1' == testCases[0].steps[0].name
    assert [] == testCases[0].steps[0].steps

    assert 3 == testCases[1].steps.size()
    assert 'step1' == testCases[1].steps[0].name
    assert [] == testCases[1].steps[0].steps
    assert 'step2' == testCases[1].steps[1].name
    assert [] == testCases[1].steps[1].steps
    assert 'step3' == testCases[1].steps[2].name
    assert [] == testCases[1].steps[2].steps
  }

  @Test
  void should_fail_when_test_function_is_not_defined() {
    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """

    props.functions = [:]
    try {
      testCaseTask.parse(input)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert 'Undefined function; name=fn_1' == e.message
    }
  }

  @Test
  void should_parse_steps_that_call_higher_order_functions() {
    def functionFile = """
      function "SingleStep" {
        path "/foo/path/to/fn"
        description "foo-description"
      }
      function "MultiStep" {
        description "foo-description"
        step "SingleStep", "singleStep", { }
      }
    """
    def functions = Function.parse(functionFile)
    def input = """
      test "test1" {
        description "test1"
        step "SingleStep", "singleStep", { }
        step "MultiStep", "multiStep", { }
        assertions { eq '1', '1' }
      }

      test "test2" {
        description "test2"
        step "SingleStep", "singleStep", { }
        step "MultiStep", "multiStep", { }
        assertions { eq '1', '1' }
      }
    """

    props.functions = [:]
    functions.each {
      props.functions[it.name] = it
    }

    def testCases = testCaseTask.parse(input)

    assert 2 == testCases.size()

    assert 2 == testCases[0].steps.size()
    assert 'singleStep' == testCases[0].steps[0].name
    assert [] == testCases[0].steps[0].steps
    assert 'multiStep' == testCases[0].steps[1].name
    assert 1 == testCases[0].steps[1].steps.size()
    assert 'singleStep' == testCases[0].steps[1].steps[0].name
    assert [] == testCases[0].steps[1].steps[0].steps

    assert 2 == testCases[1].steps.size()
    assert 'singleStep' == testCases[1].steps[0].name
    assert [] == testCases[1].steps[0].steps
    assert 'multiStep' == testCases[1].steps[1].name
    assert 1 == testCases[1].steps[1].steps.size()
    assert 'singleStep' == testCases[1].steps[1].steps[0].name
    assert [] == testCases[1].steps[1].steps[0].steps
  }

  @Test
  void should_filter_test_cases_by_test() {
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    def input = """
      test "test1" {
        description "test1 description"
        tags 'foo'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test2" {
        description "test1 description"
        step "fn_1", "step2", {
            arg1 1
            arg2 2
        }
      }
      test "test3" {
        description "test1 description"
        tags 'bar'
        step "fn_1", "step3", {
            arg1 1
            arg2 2
        }
      }
    """
    props.functions = functions
    props.test = 'test2'
    def testCases = testCaseTask.parse(input)

    assert testCases.size() == 1
    assert testCases[0].name == 'test2'
  }

  @Test
  void should_filter_test_cases_by_tags() {
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    def input = """
      test "test1" {
        description "test1 description"
        tags 'foo'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test2" {
        description "test1 description"
        step "fn_1", "step2", {
            arg1 1
            arg2 2
        }
      }
      test "test3" {
        description "test1 description"
        tags 'bar'
        step "fn_1", "step3", {
            arg1 1
            arg2 2
        }
      }
    """
    def testCases

    props.functions = functions
    props.includeTags = 'foo,bar'
    testCases = testCaseTask.parse(input)

    assert testCases.size() == 2
    assert testCases[0].name == 'test1'
    assert testCases[1].name == 'test3'

    props.functions = functions
    props.includeTags = null
    testCases = testCaseTask.parse(input)

    assert testCases.size() == 3
    assert testCases[0].name == 'test1'
    assert testCases[1].name == 'test2'
    assert testCases[2].name == 'test3'
  }

  @Test
  void should_exclude_by_excludeTags() {
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    def input = """
      test "test1" {
        description "description"
        tags 'foo', 'baz'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test2" {
        description "description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test3" {
        description "description"
        tags 'bar'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    // Include and exclude
    def testCases

    props.functions = functions
    props.includeTags = 'foo,bar'
    props.excludeTags = 'baz'

    testCases = testCaseTask.parse(input)
    assert testCases.size() == 1
    assert testCases[0].name == 'test3'

    // Include only
    testCases = []
    props.functions = functions
    props.includeTags = 'foo,bar'
    props.excludeTags = null

    testCases = testCaseTask.parse(input)
    assert testCases.size() == 2
    assert testCases[0].name == 'test1'
    assert testCases[1].name == 'test3'

    // Exclude only
    testCases = []
    props.functions = functions
    props.includeTags = null
    props.excludeTags = 'foo'

    testCases = testCaseTask.parse(input)
    assert testCases.size() == 2
    assert testCases[0].name == 'test2'
    assert testCases[1].name == 'test3'

    // No filtering
    testCases = []
    props.functions = functions
    props.includeTags = null
    props.excludeTags = null

    testCases = testCaseTask.parse(input)
    assert testCases.size() == 3
    assert testCases[0].name == 'test1'
    assert testCases[1].name == 'test2'
    assert testCases[2].name == 'test3'
  }

  @Test
  void should_not_allow_circular_function_calls() {
    def functionFile = """
      function "MultiStep1" {
        description "foo-description"

        input "foo"

        step "MultiStep2", "multiStep2", {
          foo '{{ foo }}'
        }

        output "foo", "{{ step.multiStep2.foo }}"
      }
      function "MultiStep2" {
        description "foo-description"

        input "foo"

        step "MultiStep1", "multiStep2", {
          foo '{{ foo }}'
        }

        output "foo", "{{ step.multiStep2.foo }}"
      }
    """
    def functions = Function.parse(functionFile)
    def input = """
      test "test1" {
        description "test circular reference"
        step "MultiStep1", "multiStep1", {
          foo 'bar'
        }
      }
    """

    props.functions = [:]
    functions.each {
      props.functions[it.name] = it
    }
    try {
      testCaseTask.parse(input)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert 'Circular function call detected; name=MultiStep1; callStack=[MultiStep1, MultiStep2]' == e.message
    }
  }

  @Test
  void should_parse_test_using_higher_order_function() {
    def functionFile = """
      function "SingleStep" {
        path "/foo/path/to/fn"
        description "foo-description"

        input "foo"
        output "foo"
      }
      function "MultiStep1" {
        description "foo-description"

        input "foo"

        step "SingleStep", "singleStep2", {
          foo '{{ foo }}c'
        }

        step "MultiStep2", "multiStep2", {
          foo '{{ step.singleStep2.foo }}d'
        }

        output "foo", "{{ step.multiStep2.foo }}"
      }
      function "MultiStep2" {
        description "foo-description"

        input "foo"

        step "SingleStep", "singleStep3", {
          foo '{{ foo }}e'
        }

        step "MultiStep3", "multiStep3", {
          foo '{{ step.singleStep3.foo }}f'
        }

        output "foo", "{{ step.multiStep3.foo }}"
      }
      function "MultiStep3" {
        description "foo-description"

        input "foo"

        step "SingleStep", "singleStep4", {
          foo '{{ foo }}g'
        }

        output "foo", "{{ step.singleStep4.foo }}"
      }
    """
    def functions = Function.parse(functionFile)
    def input = """
      test "test1" {
        description "test multi step function"
        step "SingleStep", "singleStep1", {
          foo 'a'
        }
        step "MultiStep1", "multiStep1", {
            foo '{{ step.singleStep1.foo }}b'
        }
      }
    """

    props.functions = [:]
    functions.each {
      props.functions[it.name] = it
    }

    def testCases = testCaseTask.parse(input)
    assert 1 == testCases.size()

    def tree = Step.getTree(testCases[0].steps)
    assert 7 == tree.size()
    assert '|-- SingleStep:singleStep1' == tree[0]
    assert '|-- MultiStep1:multiStep1' == tree[1]
    assert '|   |-- SingleStep:singleStep2' == tree[2]
    assert '|   |-- MultiStep2:multiStep2' == tree[3]
    assert '|   |   |-- SingleStep:singleStep3' == tree[4]
    assert '|   |   |-- MultiStep3:multiStep3' == tree[5]
    assert '|   |   |   |-- SingleStep:singleStep4' == tree[6]
  }

  @Test
  void should_not_allow_step_variables_to_leak_into_higher_order_functions() {
    def functionFile = """
      function "SingleStep" {
        path "/foo/path/to/fn"
        description "foo-description"

        input "foo"
        output "foo"
      }
      function "HigherOrderFunc" {
        description "foo-description"

        step "SingleStep", "singleStep2", {
          foo '{{ step.singleStep1.foo }}'
        }
      }
    """
    def input = """
      test "test1" {
        description "test multi step function"
        step "SingleStep", "singleStep1", {
          foo 'a'
        }
        step "HigherOrderFunc", "higherOrderFunc", {
        }
      }
    """

    props.functions = [:]
    Function.parse(functionFile).each {
      props.functions[it.name] = it
    }

    try {
      testCaseTask.parse(input).each({ testCase ->
        props.testCase = testCase
        Step.eachStep(testCase.steps, props, {})
      })
      fail("Expected StepNotFoundException")
    }
    catch(StepNotFoundException e) {
      assert e.message == 'Could not resolve values due to undefined step; testCase=test1; step=singleStep2; missingStepReference=step.singleStep1'
    }
  }
}

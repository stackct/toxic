package toxic.dsl

import org.junit.Test

import static org.junit.Assert.fail

class TestCaseTaskTest {
  private ConfigObject props
  private TestCaseTask testCaseTask

  TestCaseTaskTest() {
    props = [:]
    testCaseTask = new TestCaseTask(props: props)
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
    testCaseTask.parse(input)

    def assertSequence = { int testCaseIndex, def expectedSeq ->
      def actualSeq = props.testCases[testCaseIndex].stepSequence
      assert expectedSeq.size() == actualSeq.size()
      expectedSeq.eachWithIndex { it, i ->
        assert it == actualSeq[i].step.name
      }
    }

    assert 2 == props.testCases.size()
    assertSequence(0, ['step1'])
    assertSequence(1, ['step1', 'step2', 'step3'])
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
  void should_set_step_index_and_level_during_init() {
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

    def assertSequence = { int testCaseIndex, int sequenceIndex, String expectedStepName, int expectedLevel ->
      def actualSeq = props.testCases[testCaseIndex].stepSequence[sequenceIndex]
      assert expectedStepName == actualSeq.step.name
      assert expectedLevel == actualSeq.level
    }

    testCaseTask.parse(input)
    assert 2 == props.testCases.size()
    assert 3 == props.testCases[0].stepSequence.size()
    assert 3 == props.testCases[1].stepSequence.size()
    assertSequence(0, 0, 'singleStep', 0)
    assertSequence(0, 1, 'multiStep', 0)
    assertSequence(0, 2, 'singleStep', 1)
    assertSequence(1, 0, 'singleStep', 0)
    assertSequence(1, 1, 'multiStep', 0)
    assertSequence(1, 2, 'singleStep', 1)
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
    props.testCases = []
    props.functions = functions
    props.test = 'test2'
    testCaseTask.parse(input)

    assert props.testCases.size() == 1
    assert props.testCases[0].name == 'test2'
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
    props.testCases = []
    props.functions = functions
    props.includeTags = 'foo,bar'
    testCaseTask.parse(input)

    assert props.testCases.size() == 2
    assert props.testCases[0].name == 'test1'
    assert props.testCases[1].name == 'test3'

    props.testCases = []
    props.functions = functions
    props.includeTags = null
    testCaseTask.parse(input)

    assert props.testCases.size() == 3
    assert props.testCases[0].name == 'test1'
    assert props.testCases[1].name == 'test2'
    assert props.testCases[2].name == 'test3'
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
    props.testCases = []
    props.functions = functions
    props.includeTags = 'foo,bar'
    props.excludeTags = 'baz'

    testCaseTask.parse(input)
    assert props.testCases.size() == 1
    assert props.testCases[0].name == 'test3'

    // Include only
    props.testCases = []
    props.functions = functions
    props.includeTags = 'foo,bar'
    props.excludeTags = null

    testCaseTask.parse(input)
    assert props.testCases.size() == 2
    assert props.testCases[0].name == 'test1'
    assert props.testCases[1].name == 'test3'

    // Exclude only
    props.testCases = []
    props.functions = functions
    props.includeTags = null
    props.excludeTags = 'foo'

    testCaseTask.parse(input)
    assert props.testCases.size() == 2
    assert props.testCases[0].name == 'test2'
    assert props.testCases[1].name == 'test3'

    // No filtering
    props.testCases = []
    props.functions = functions
    props.includeTags = null
    props.excludeTags = null

    testCaseTask.parse(input)
    assert props.testCases.size() == 3
    assert props.testCases[0].name == 'test1'
    assert props.testCases[1].name == 'test2'
    assert props.testCases[2].name == 'test3'
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

    testCaseTask.parse(input)
    assert 1 == props.testCases.size()

    def seq = props.testCases[0].stepSequence
    assert 7 == seq.size()
    assert 'singleStep1' == seq[0].step.name
    assert 'multiStep1' == seq[1].step.name
    assert 'singleStep2' == seq[2].step.name
    assert 'multiStep2' == seq[3].step.name
    assert 'singleStep3' == seq[4].step.name
    assert 'multiStep3' == seq[5].step.name
    assert 'singleStep4' == seq[6].step.name
  }
}

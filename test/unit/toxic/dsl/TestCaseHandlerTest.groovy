package toxic.dsl

import org.apache.log4j.Level
import toxic.ToxicProperties
import toxic.dir.DirItem

import static org.junit.Assert.fail
import org.junit.Test

class TestCaseHandlerTest {
  @Test
  void should_construct() {
    DirItem dirItem = new DirItem('something.test')
    def props = ['arg1': 'value1', 'arg2': 'value2']
    TestCaseHandler testCaseHandler = new TestCaseHandler(dirItem, props)
    assert dirItem == testCaseHandler.item
    assert props == testCaseHandler.props
  }

  @Test
  void should_parse_test_case_file_during_lazy_init() {
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
        
        step "fn_2", "step1", {
            arg1 1
            arg2 2
        }
        
        step "fn_3", "step1", {
            arg1 1
            arg2 2
        }
      }
    """

    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1'), 'fn_2': new Function(path: 'fn_2'), 'fn_3': new Function(path: 'fn_3')]
    def props = [stepIndex: 10, testCases: [], functions: functions]
    mockFile(input) { file ->
      new TestCaseHandler(dirItem, props).lazyInit(file)
    }
    assert 0 == props.stepIndex
    assert 2 == props.testCases.size()
    def expectedPaths = ['fn_1', [name: 'test1_assertions'], 'fn_1', 'fn_2', 'fn_3', [name: 'test2_assertions']]
    assert expectedPaths.size() == dirItem.children.size()
    expectedPaths.eachWithIndex { expectedPath, index ->
      if(expectedPath instanceof Map) {
        TransientDir assertionDir = dirItem.children[index].file
        assert 1 == assertionDir.listFiles().size()
        assert assertionDir.listFiles()[0].name.startsWith(expectedPath.name)
      }
      else {
        assert expectedPath == dirItem.children[index].file.path
      }
      assert dirItem == dirItem.children[index].parent
    }
  }

  @Test
  void should_fail_init_when_test_function_is_not_defined() {
    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """

    DirItem dirItem = new DirItem('something.test')
    def props = [stepIndex: 10, testCases: [], functions: [:]]
    mockFile(input) { file ->
      try {
        new TestCaseHandler(dirItem, props).lazyInit(file)
        fail('Expected IllegalStateException')
      }
      catch(IllegalStateException e) {
        assert 'Undefined function; name=fn_1' == e.message
      }
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
    def testFile = """
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

    def props = [functions: [:]]
    functions.each {
      props.functions[it.name] = it
    }
    DirItem dirItem = new DirItem('something.test')

    def assertSequence = { int sequenceIndex, String expectedStepName, int expectedLevel ->
      assert expectedStepName == props.stepSequence[sequenceIndex].step.name
      assert expectedLevel == props.stepSequence[sequenceIndex].level
    }

    mockFile(testFile) { file ->
      new TestCaseHandler(dirItem, props).lazyInit(file)
      assert 8 == props.stepSequence.size()
      assertSequence(0, 'singleStep', 0)
      assertSequence(1, 'multiStep', 0)
      assertSequence(2, 'singleStep', 1)
      assertSequence(3, null, 0)
      assertSequence(4, 'singleStep', 0)
      assertSequence(5, 'multiStep', 0)
      assertSequence(6, 'singleStep', 1)
      assertSequence(7, null, 0)
    }
  }

  @Test
  void should_init_once_and_only_once() {
    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    assert [] == dirItem.children

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 2 == dirItem.children.size()

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 2 == dirItem.children.size()
    }
  }

  @Test
  void should_filter_test_cases_by_tags() {
    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    assert [] == dirItem.children

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
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test3" {
        description "test1 description"
        tags 'bar'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions, includeTags:'foo,bar'])

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 4 == dirItem.children.size()
      assert props.testCases.size() == 2
      assert props.testCases[0].name == 'test1'
      assert props.testCases[1].name == 'test3'

      props = new ToxicProperties([functions: functions])
      dirItem.children = []

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 6 == dirItem.children.size()
      assert props.testCases.size() == 3
      assert props.testCases[0].name == 'test1'
      assert props.testCases[1].name == 'test2'
      assert props.testCases[2].name == 'test3'
    }
  }

  @Test
  public void should_exclude_by_excludeTags() {
    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    assert [] == dirItem.children

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
    mockFile(input) { file ->
      // Include and exclude
      def props = new ToxicProperties([functions: functions, includeTags:'foo,bar', excludeTags:'baz'])
      new TestCaseHandler(dirItem, props).nextFile(file)
      assert props.testCases.size() == 1
      assert props.testCases[0].name == 'test3'

      // Include only
      props = new ToxicProperties([functions: functions, includeTags:'foo,bar'])
      dirItem.children = []

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert props.testCases.size() == 2
      assert props.testCases[0].name == 'test1'
      assert props.testCases[1].name == 'test3'

      // Exclude only
      props = new ToxicProperties([functions: functions, excludeTags:'foo'])
      dirItem.children = []

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert props.testCases.size() == 2
      assert props.testCases[0].name == 'test2'
      assert props.testCases[1].name == 'test3'

      // No filtering
      props = new ToxicProperties([functions: functions])
      dirItem.children = []

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert props.testCases.size() == 3
      assert props.testCases[0].name == 'test1'
      assert props.testCases[1].name == 'test2'
      assert props.testCases[2].name == 'test3'
    }
  }

  @Test
  void should_run_single_test_case() {
    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    assert [] == dirItem.children

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
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
      test "test3" {
        description "test1 description"
        tags 'bar'
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions, tags:'test1,test2', test:'test3'])

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 2 == dirItem.children.size()
      assert props.testCases.size() == 1
      assert props.testCases[0].name == 'test3'
    }
  }

  @Test
  void should_run_higher_order_function_step() {
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
    def testFile = """
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

    def props = new ToxicProperties([functions: [:]])
    functions.each {
      props.functions[it.name] = it
    }
    DirItem dirItem = new DirItem('something.test')

    def assertInputs = { def expectedInputs ->
      expectedInputs.each { k, v ->
        assert v == props[k]
      }
    }

    def assertOutputs = { Step step, def expectedOutputs ->
      expectedOutputs.each { k, v ->
        assert v == step.outputs[k]
      }
    }

    def assertInitFile = { File file, def expectedInputs, def expectedOutputs ->
      StepFile nextFile = new TestCaseHandler(dirItem, props).nextFile(file)
      assertInputs(expectedInputs)
      Step step = TestCaseHandler.currentStep(props)
      nextFile.complete(props)
      assertOutputs(step, expectedOutputs)
    }

    def assertNextFile = { File file, def expectedInputs, def expectedOutputs ->
      assertInputs(expectedInputs)
      File nextFile = new TestCaseHandler(dirItem, props).nextFile(file)
      Step step = TestCaseHandler.currentStep(props)
      if(nextFile instanceof TransientFile) {
        nextFile = new TestCaseHandler(dirItem, props).nextFile(file)
      }
      if(nextFile instanceof StepFile) {
        nextFile.complete(props)
      }
      assertOutputs(step, expectedOutputs)
    }

    mockFile(testFile) { file ->
      TestCaseHandler.log.track { logger ->
        assertInitFile(file, [foo:'a'], [foo:'a'])
        assertNextFile(file, [foo:'abc'], [foo:'abc'])
        assertNextFile(file, [foo:'abcde'], [foo:'abcde'])
        assertNextFile(file, [foo:'abcdefg'], [foo:'abcdefg'])
        assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
        assert null == new TestCaseHandler(dirItem, props).nextFile(file)

        assert logger.isLogged('Executing step; test="test1"; name=singleStep1; fnName=SingleStep; fnPath=/foo/path/to/fn', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=multiStep1; fnName=MultiStep1; subSteps=2', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=singleStep2; fnName=SingleStep; fnPath=/foo/path/to/fn', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=multiStep2; fnName=MultiStep2; subSteps=2', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=singleStep3; fnName=SingleStep; fnPath=/foo/path/to/fn', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=multiStep3; fnName=MultiStep3; subSteps=1', Level.INFO)
        assert logger.isLogged('Executing step; test="test1"; name=singleStep4; fnName=SingleStep; fnPath=/foo/path/to/fn', Level.INFO)
      }
    }
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
    def testFile = """
      test "test1" {
        description "test circular reference"
        step "MultiStep1", "multiStep1", {
          foo 'bar'
        }
      }
    """

    def props = [functions: [:]]
    functions.each {
      props.functions[it.name] = it
    }
    DirItem dirItem = new DirItem('something.test')
    mockFile(testFile) { file ->
      try {
        new TestCaseHandler(dirItem, props).nextFile(file)
        fail('Expected IllegalStateException')
      }
      catch(IllegalStateException e) {
        assert 'Circular function call detected; name=MultiStep1; callStack=[MultiStep1, MultiStep2]' == e.message
      }
    }
  }

  @Test
  void should_find_current_test_case() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(name: 'tc1', steps: testCase1Steps), new TestCase(name: 'tc2', steps: testCase2Steps)]

    ToxicProperties props = new ToxicProperties()
    props.testCases = testCases
    props.stepIndex = 0

    assert 'tc1' == TestCaseHandler.currentTestCase(props).name
    props.stepIndex++
    assert 'tc1' == TestCaseHandler.currentTestCase(props).name
    props.stepIndex++
    assert 'tc2' == TestCaseHandler.currentTestCase(props).name
    props.stepIndex++
    assert 'tc2' == TestCaseHandler.currentTestCase(props).name
    props.stepIndex++
    assert 'tc2' == TestCaseHandler.currentTestCase(props).name
    props.stepIndex++
    assert null == TestCaseHandler.currentTestCase(props)
  }

  @Test
  void should_return_next_file() {
    DirItem dirItem = new DirItem('something.test')
    def functions = ['fn_1': new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])]
    assert [] == dirItem.children

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }

  @Test
  void should_remove_optional_args_from_memory_map() {
    def functions = [
      'fn_1': new Function(path: 'fn_1', args: [
        new Arg(name: 'foo', required: true),
        new Arg(name: 'bar', required: false),
        new Arg(name: 'baz', required: false),
      ])
    ]

    DirItem dirItem = new DirItem('something.test')
    
    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
          foo "remove me"
        }
      }
    """

    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      props.output1 = 'existing.property.dont.remove'
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      props['bar'] = 'and me'
      props['baz'] = 'and me too'
      
      TestCaseHandler.completeCurrentStep(props)
      assert !props.containsKey('foo')
      assert !props.containsKey('bar')
      assert !props.containsKey('baz')
    }
  }

  @Test
  void should_move_output_arg_results_to_step() {
    DirItem dirItem = new DirItem('something.test')
    Function fn1 = new Function(path: 'fn_1', outputs: ['output1': null, 'output2': null], args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function fn2 = new Function(path: 'fn_2', outputs: ['output3': null, 'output4': null], args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    def functions = ['fn_1': fn1, 'fn_2': fn2]
    assert [] == dirItem.children

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
        step "fn_2", "step1", {
            arg1 1
            arg2 2
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      props.output1 = 'existing.property.dont.remove'
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      props.output1 = 'value1'
      props.output2 = 'value2'
      TestCaseHandler.completeCurrentStep(props)
      assert ['output1': 'value1', 'output2': 'value2'] == props.testCases[0].steps[0].outputs
      assert 'existing.property.dont.remove' == props.output1
      assert !props.containsKey('output2')

      TestCaseHandler.startNextStep(props)
      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      props.output3 = 'value3'
      props.output4 = 'value4'
      TestCaseHandler.completeCurrentStep(props)
      assert !props.containsKey('output3')
      assert !props.containsKey('output4')
      assert ['output3': 'value3', 'output4': 'value4'] == props.testCases[0].steps[1].outputs

      TestCaseHandler.startNextStep(props)
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile

      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }

  @Test
  void should_preserve_existing_properties() {
    DirItem dirItem = new DirItem('something.test')
    Function fn1 = new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function fn2 = new Function(path: 'fn_2', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    def functions = ['fn_1': fn1, 'fn_2': fn2]

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
        step "fn_2", "step1", {
            arg1 3
            arg2 4
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])

      props.arg1 = "existing.arg1"

      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 1 == props.arg1
      assert 2 == props.arg2
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 3 == props.arg1
      assert 4 == props.arg2
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert 'existing.arg1' == props.arg1
      assert !props.containsKey('arg2')

      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }

  @Test
  void should_copy_step_variables_to_memory_map() {
    DirItem dirItem = new DirItem('something.test')
    Function fn1 = new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function fn2 = new Function(path: 'fn_2', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    def functions = ['fn_1': fn1, 'fn_2': fn2]

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
        step "fn_2", "step1", {
            arg1 3
            arg2 4
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 1 == props.arg1
      assert 2 == props.arg2
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 3 == props.arg1
      assert 4 == props.arg2
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert !props.containsKey('arg1')
      assert !props.containsKey('arg2')

      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }

  @Test
  void should_set_retry_properties() {
    DirItem dirItem = new DirItem('something.test')
    Function fn1 = new Function(path: 'fn_1', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function fn2 = new Function(path: 'fn_2', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    def functions = ['fn_1': fn1, 'fn_2': fn2]

    def input = """
      test "test1" {
        description "test1 description"
        step "fn_1", "step1", {
            arg1 1
            arg2 2
        }
        step "fn_2", "step1", {
          wait {
            timeoutMs  30
            intervalMs  5
            
            condition {
              eq '1', '1'
            }
          }
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert props['task.retry.atMostMs'] == 30
      assert props['task.retry.every'] == 5
      assert props['task.retry.condition'] instanceof Closure
      TestCaseHandler.completeCurrentStep(props)

      TestCaseHandler.startNextStep(props)
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile

      assert !props.containsKey('task.retry.atMostMs')
      assert !props.containsKey('task.retry.every')
      assert !props.containsKey('task.retry.condition')

      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }
  @Test
  void should_allow_input_variables_to_be_assigned_to_output_variables() {
    DirItem dirItem = new DirItem('something.test')
    Function fn = new Function(path: 'fn1', args: [new Arg(name: 'foo')], outputs: ['foo': null])
    def functions = ['fn1': fn]

    def input = """
      test "test1" {
        description "test1 description"
        step "fn1", "step1", {
            foo 'bar'
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions])
      assert 'fn1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      TestCaseHandler.completeCurrentStep(props)
      TestCaseHandler.startNextStep(props)
      assert 'bar' == props.step.step1.foo
    }
  }

  @Test
  void should_not_override_existing_property_with_reusing_properties() {
    DirItem dirItem = new DirItem('something.test')
    Function fn = new Function(path: 'fn1', args: [new Arg(name: 'foo')], outputs: ['foo': null])
    def functions = ['fn1': fn]

    def input = """
      test "test1" {
        description "test1 description"
        step "fn1", "step1", {
            foo 'bar'
        }
      }
    """
    mockFile(input) { file ->
      def props = new ToxicProperties([functions: functions, foo: 'foobar'])
      assert 'fn1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      TestCaseHandler.completeCurrentStep(props)
      TestCaseHandler.startNextStep(props)
      assert 'bar' == props.step.step1.foo
      assert 'foobar' == props.foo
    }
  }

  @Test
  void should_copy_interpolated_values_to_memory_map() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'create_order', function: 'create_order', outputs: [orderId: '12345', amount: 1000, map: [key: 'value']])
    testCase.steps << new Step(name: 'void_order', function: 'void_order', args: [total: '{{ step.create_order.amount }}'
                                                        , list: ['{{step.create_order.orderId}}' , '{{step.create_order.amount}}' , 'test', true]
                                                        , order: [orderId: '{{step.create_order.orderId}}', amount: '{{step.create_order.amount}}']
                                                        , map: '{{ step.create_order.map }}'
    ])

    Function voidFunction = new Function(args: [new Arg(name: 'total'), new Arg(name: 'list'), new Arg(name: 'order'), new Arg(name: 'map')])
    def props = [testCases: [testCase], stepIndex: 1, stepSequence: [], functions: [create_order:new Function(), void_order:voidFunction]]
    testCase.steps.each {
      props.stepSequence << [step: it, level: 0]
    }
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert '12345' == props.order.orderId
    assert 1000 == props.order.amount
    assert 1000 == props.total
    assert ['12345', 1000, 'test', true] == props.list
    assert [key: 'value'] == props.map
  }

  @Test
  void should_copy_default_value_to_memory_map() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn')

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: true, defaultValue: 'bar')])
    def props = [testCases: [testCase], stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_copy_interpolated_default_value_to_memory_map() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn')

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: true, defaultValue: '{{ var.foo }}')])
    def props = [testCases: [testCase], stepIndex: 0, functions: [foo_fn: fn], var: [foo: 'bar']]
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_not_copy_default_value_to_memory_map_when_default_value_is_not_defined() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn')

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: false)])
    def props = [testCases: [testCase], stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert !props.containsKey('foo')
  }

  @Test
  void should_not_override_step_arg_with_default_arg() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn', args: [foo: 'bar'])

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: true, defaultValue: 'foobar')])
    def props = [testCases: [testCase], stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_validate_all_required_input_args_are_present() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'create an order', function: 'create_order', args: [:])

    def props = [testCases: [testCase], stepIndex: 0]
    props.step = new StepOutputResolver(props)

    Function function = new Function(name: 'create_order', args: [new Arg(name: 'someRequiredArg', required: true)])
    props.functions = [create_order: function]

    try {
      TestCaseHandler.copyStepArgsToMemory(props)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert 'Missing required args for function; name=create_order; args=[someRequiredArg]' == e.message
    }
  }

  @Test
  void should_validate_arg_is_defined_on_function() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'create an order', function: 'create_order', args: ['not-defined':'someValue'])

    def props = [testCases: [testCase], stepIndex: 0]
    props.step = new StepOutputResolver(props)

    Function function = new Function(name: 'create_order', args: [])
    props.functions = [create_order: function]

    try {
      TestCaseHandler.copyStepArgsToMemory(props)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert 'Arg is not defined for function; name=create_order; arg=not-defined' == e.message
    }
  }

  @Test
  void should_output_resolution_from_interpolated_string() {
    def input = """
      test "test1" {
        description "test1 description"

        declare {
          foo 1
        }

        step "fn_1", "one_line_item", {
            arg1 "{{ var.foo }}"
        }

        assertions {
          eq 'ordering-1', "{{ step.one_line_item.screenName }}"
          neq 'order', "{{ step.one_line_item.screenName }}"
        }
      }
    """
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.test')
      Function fn1 = new Function(path: 'fn_1', outputs: ['screenName': null], args: [new Arg(name: 'arg1')])
      def props = new ToxicProperties([functions: ['fn_1': fn1]])
      new TestCaseHandler(dirItem, props).nextFile(file)
      props.screenName = 'ordering-' + props.arg1
      TestCaseHandler.completeCurrentStep(props)
      TestCaseHandler.startNextStep(props)
      TransientFile transientFile = new TestCaseHandler(dirItem, props).nextFile(file)

      def expected = new StringBuffer()
      expected.append("assert 'ordering-1' == 'ordering-1' : \"ordering-1 ==  step.one_line_item.screenName \"")
      expected.append("\n")
      expected.append("assert 'order' != 'ordering-1' : \"order !=  step.one_line_item.screenName \"")
      expected.append("\n")

      assert expected.toString() == transientFile.text
    }
  }

  @Test
  void should_find_current_step() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(steps: testCase1Steps), new TestCase(steps: testCase2Steps)]

    ToxicProperties props = new ToxicProperties()
    props.testCases = testCases
    props.stepIndex = 0

    assert 'step1' == TestCaseHandler.currentStep(props).name
    props.stepIndex++
    assert 'step2' == TestCaseHandler.currentStep(props).name
    props.stepIndex++
    assert 'step3' == TestCaseHandler.currentStep(props).name
    props.stepIndex++
    assert 'step4' == TestCaseHandler.currentStep(props).name
    props.stepIndex++
    assert 'step5' == TestCaseHandler.currentStep(props).name
    props.stepIndex++
    assert null == TestCaseHandler.currentStep(props)
  }

  def mockFile(String fileText, Closure c) {
    def file
    try {
      file = File.createTempFile('testCaseHandler', '.test')
      file.text = fileText
      c(file)
    }
    finally {
      file?.delete()
    }
  }
}

package toxic.dsl

import toxic.dir.DirItem

import static org.junit.Assert.fail
import org.junit.Test
import org.junit.Ignore

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
        assert dirItem.children[index].file.name.startsWith(expectedPath.name)
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
      def props = [functions: functions]

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
      def props = [functions: functions, tags:'foo,bar']

      new TestCaseHandler(dirItem, props).nextFile(file)
      assert 4 == dirItem.children.size()
      assert props.testCases.size() == 2
      assert props.testCases[0].name == 'test1'
      assert props.testCases[1].name == 'test3'

      props = [functions: functions]
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
      def props = [functions: functions]
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
    }
  }

  @Test
  void should_move_output_arg_results_to_step() {
    DirItem dirItem = new DirItem('something.test')
    Function fn1 = new Function(path: 'fn_1', outputs: ['output1', 'output2'], args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function fn2 = new Function(path: 'fn_2', outputs: ['output3', 'output4'], args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
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
      def props = [functions: functions]
      props.output1 = 'existing.property.dont.remove'
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      props.output1 = 'value1'
      props.output2 = 'value2'
      TestCaseHandler.stepComplete(props)
      assert ['output1': 'value1', 'output2': 'value2'] == props.testCases[0].steps[0].outputs
      assert 'existing.property.dont.remove' == props.output1
      assert !props.containsKey('output2')

      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      props.output3 = 'value3'
      props.output4 = 'value4'
      TestCaseHandler.stepComplete(props)
      assert !props.containsKey('output3')
      assert !props.containsKey('output4')
      assert ['output3': 'value3', 'output4': 'value4'] == props.testCases[0].steps[1].outputs

      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      TestCaseHandler.stepComplete(props)

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
      def props = [functions: functions]

      props.arg1 = "existing.arg1"

      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 1 == props.arg1
      assert 2 == props.arg2
      TestCaseHandler.stepComplete(props)

      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 3 == props.arg1
      assert 4 == props.arg2
      TestCaseHandler.stepComplete(props)

      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert 'existing.arg1' == props.arg1
      TestCaseHandler.stepComplete(props)
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
      def props = [functions: functions]
      assert 'fn_1' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 1 == props.arg1
      assert 2 == props.arg2
      TestCaseHandler.stepComplete(props)

      assert 'fn_2' == new TestCaseHandler(dirItem, props).nextFile(file).name
      assert 3 == props.arg1
      assert 4 == props.arg2
      TestCaseHandler.stepComplete(props)

      assert new TestCaseHandler(dirItem, props).nextFile(file) instanceof TransientFile
      assert !props.containsKey('arg1')
      assert !props.containsKey('arg2')
      TestCaseHandler.stepComplete(props)

      assert null == new TestCaseHandler(dirItem, props).nextFile(file)
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
    def props = [testCases: [testCase], stepIndex: 1, functions: [create_order:new Function(), void_order:voidFunction]]
    props.step = new StepOutputResolver(props)
    TestCaseHandler.copyStepArgsToMemory(props)
    assert '12345' == props.order.orderId
    assert 1000 == props.order.amount
    assert 1000 == props.total
    assert ['12345', 1000, 'test', true] == props.list
    assert [key: 'value'] == props.map
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
        step "fn_1", "one_line_item", {
            arg1 1
        }
        assertions {
          eq 'ordering', "{{ step.one_line_item.screenName }}"
          neq 'order', "{{ step.one_line_item.screenName }}"
        }
      }
    """
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.test')
      Function fn1 = new Function(path: 'fn_1', outputs: ['screenName'], args: [new Arg(name: 'arg1')])
      def props = [functions: ['fn_1': fn1]]
      new TestCaseHandler(dirItem, props).nextFile(file)
      props.screenName = 'ordering'
      TestCaseHandler.stepComplete(props)
      TransientFile transientFile = new TestCaseHandler(dirItem, props).nextFile(file)

      def expected = new StringBuffer()
      expected.append("assert 'ordering' == 'ordering' : \"ordering ==  step.one_line_item.screenName \"")
      expected.append("\n")
      expected.append("assert 'order' != 'ordering' : \"order !=  step.one_line_item.screenName \"")
      expected.append("\n")

      assert expected.toString() == transientFile.text
    }
  }

  @Test
  void should_find_current_step() {
    def testCase1Steps = [new Step(name: 'step1'), new Step(name: 'step2')]
    def testCase2Steps = [new Step(name: 'step3'), new Step(name: 'step4'), new Step(name: 'step5')]
    def testCases = [new TestCase(steps: testCase1Steps), new TestCase(steps: testCase2Steps)]

    assert 'step1' == TestCaseHandler.currentStep(testCases, 0).name
    assert 'step2' == TestCaseHandler.currentStep(testCases, 1).name
    assert 'step3' == TestCaseHandler.currentStep(testCases, 2).name
    assert 'step4' == TestCaseHandler.currentStep(testCases, 3).name
    assert 'step5' == TestCaseHandler.currentStep(testCases, 4).name
    assert null == TestCaseHandler.currentStep(testCases, 5)
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

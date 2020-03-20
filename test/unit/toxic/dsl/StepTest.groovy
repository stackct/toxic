package toxic.dsl

import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import toxic.ToxicProperties

import static org.junit.Assert.fail

class StepTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  @Test
  void should_collect_all_arguments() {
    def step = new Step(name:'the.step', function:'fred')

    step.arg1("foo")
    step.arg2("bar")
    step.arg3("one", "two", "three")

    assert step.args['arg1'] == 'foo'
    assert step.args['arg2'] == 'bar'
    assert step.args['arg3'] == ["one", "two", "three"]
  }

  @Test
  void should_not_collect_duplicate_arguments() {
    expectedException.expect(IllegalArgumentException.class)
    expectedException.expectMessage("Duplicate argument 'arg1' for function 'fred'")

    def step = new Step(name:'the.step', function:'fred')

    step.arg1("foo")
    step.arg1("bar")

    assert step.args['arg1'] == 'foo'
  }

  @Test
  void should_interpolate_string() {
    def props = [foo:'bar', 'key with spaces':'value with spaces']
    assert 'bar' == Step.interpolate(props, '{{ foo }}')
    assert 'bar' == Step.interpolate(props, ' {{ foo }} ')
    assert 'something bar else' == Step.interpolate(props, 'something {{ foo }} else')
    assert 'value with spaces' == Step.interpolate(props, '{{ key with spaces }}')
  }

  @Test
  void should_interpolate_string_with_literal_key()
  {
    def props = [ 'foo.bar': 'baz' ]
    assert 'baz' == Step.interpolate(props, "{{ `foo.bar` }}")
  }

  @Test
  void should_interpolate_nested_map() {
    def props = [foo: [foo: [foo: 'bar']]]
    assert 'bar' == Step.interpolate(props, '{{ foo.foo.foo }}')
  }

  @Test
  void should_interpolate_list() {
    def props = [foo1:'bar1', foo2:'bar2', foo3:'bar3']
    assert ['bar1', 'bar2', 'bar3'] == Step.interpolate(props, ['{{ foo1 }}', '{{ foo2 }}', '{{ foo3 }}'])
  }

  @Test
  void should_interpolate_variadic_args() {
    def props = [foo1:'bar1', foo2:'bar2', foo3:'bar3']
    assert ['bar1', 'bar2', 'bar3'] == Step.interpolate(props, ['{{ foo1 }}', '{{ foo2 }}', '{{ foo3 }}'] as String[])
  }

  @Test
  void should_interpolate_map() {
    def props = [foo1:'bar1', foo2:'bar2', foo3:'bar3']
    assert ['foo1':'bar1', 'foo2':'bar2', 'foo3':'bar3'] == Step.interpolate(props, [foo1: '{{ foo1 }}', foo2: '{{ foo2 }}', foo3: '{{ foo3 }}'])
  }

  @Test
  void should_get_prefix() {
    assert null == new Step(function:'Fn').prefix
    assert null == new Step(function:'.Fn').prefix
    assert null == new Step(function:'Fn.').prefix
    assert "foo" == new Step(function:'foo.Fn').prefix
  }

  @Test
  void should_inherit_prefix() {

    def apply = { String s, String p = null -> 
      def step = new Step(function:s)
      def parent
      if (p) {
        parent = new Step(function:p)
      }
      step.inheritPrefix(parent)
      return step.function
    }

    assert "Bar"   == apply('Bar')             // No parent
    assert "Bar"   == apply('Bar', 'Foo')      // noop
    assert "a.Bar" == apply('Bar', 'a.Foo')    // Parent has prefix, child does not
    assert "b.Bar" == apply('b.Bar', 'a.Foo')  // Parent has prefix, but so does child
  }

  @Test
  void should_get_function() {
    def functions = [:]
    def props = [functions: functions]
    assert null == new Step(function: 'foo').getFunction(props)

    functions.foo = new Function(name: 'bar')
    assert 'bar' == new Step(function: 'foo').getFunction(props).name
  }

  @Test
  void should_copy_interpolated_values_to_memory_map() {
    TestCase testCase = new TestCase(stepSequence: [])
    testCase.steps << new Step(name: 'create_order', function: 'create_order', outputs: [orderId: '12345', amount: 1000, map: [key: 'value']])
    testCase.steps << new Step(name: 'void_order', function: 'void_order', args: [total: '{{ step.create_order.amount }}'
      , list: ['{{step.create_order.orderId}}' , '{{step.create_order.amount}}' , 'test', true]
      , order: [orderId: '{{step.create_order.orderId}}', amount: '{{step.create_order.amount}}']
      , map: '{{ step.create_order.map }}'
    ])

    Function voidFunction = new Function(args: [new Arg(name: 'total'), new Arg(name: 'list'), new Arg(name: 'order'), new Arg(name: 'map')])
    def props = [testCase: testCase, stepIndex: 1, functions: [create_order:new Function(), void_order:voidFunction]]
    testCase.steps.each {
      testCase.stepSequence << [step: it, level: 0]
    }
    props.step = new StepOutputResolver(props)
    testCase.steps[props.stepIndex].copyArgsToMemory(props)
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
    def props = [testCase: testCase, stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    testCase.steps[props.stepIndex].copyArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_copy_interpolated_default_value_to_memory_map() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn')

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: true, defaultValue: '{{ var.foo }}')])
    def props = [testCase: testCase, stepIndex: 0, functions: [foo_fn: fn], var: [foo: 'bar']]
    props.step = new StepOutputResolver(props)
    testCase.steps[props.stepIndex].copyArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_not_copy_default_value_to_memory_map_when_default_value_is_not_defined() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn')

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: false)])
    def props = [testCase: testCase, stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    testCase.steps[props.stepIndex].copyArgsToMemory(props)
    assert !props.containsKey('foo')
  }

  @Test
  void should_not_override_step_arg_with_default_arg() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'foo_step', function: 'foo_fn', args: [foo: 'bar'])

    Function fn = new Function(args: [new Arg(name: 'foo', hasDefaultValue: true, defaultValue: 'foobar')])
    def props = [testCase: testCase, stepIndex: 0, functions: [foo_fn: fn]]
    props.step = new StepOutputResolver(props)
    testCase.steps[props.stepIndex].copyArgsToMemory(props)
    assert 'bar' == props.foo
  }

  @Test
  void should_validate_all_required_input_args_are_present() {
    TestCase testCase = new TestCase()
    testCase.steps << new Step(name: 'create an order', function: 'create_order', args: [:])

    def props = [testCase: testCase, stepIndex: 0]
    props.step = new StepOutputResolver(props)

    Function function = new Function(name: 'create_order', args: [new Arg(name: 'someRequiredArg', required: true)])
    props.functions = [create_order: function]

    try {
      testCase.steps[props.stepIndex].copyArgsToMemory(props)
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

    def props = [testCase: testCase, stepIndex: 0]
    props.step = new StepOutputResolver(props)

    Function function = new Function(name: 'create_order', args: [])
    props.functions = [create_order: function]

    try {
      testCase.steps[props.stepIndex].copyArgsToMemory(props)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert 'Arg is not defined for function; name=create_order; arg=not-defined' == e.message
    }
  }

  @Test
  void should_copy_wait_values_to_memory_map() {
    Function function = new Function(name: 'fn1')
    ToxicProperties props = [functions: [fn1: function]]
    new Step(function: 'fn1', wait: new Wait( timeoutMs: 30, intervalMs: 5, successes: 10)).copyArgsToMemory(props)
    assert props['task.retry.atMostMs'] == 30
    assert props['task.retry.every'] == 5
    assert props['task.retry.successes'] == 10
    assert props['task.retry.condition'] instanceof Closure
  }

  @Test
  void should_not_traverse_steps_when_foreach_is_null() {
    def step = new Step(name:'name', function:'fn')
    def steps = []
    step.eachStep([:], { stepItem ->
      steps << stepItem
    })
    assert 1 == steps.size()
    assert step == steps[0]
  }

  @Test
  void should_interpolate_the_foreach_item() {
    def step = new Step(name:'name', function:'fn')
    step.foo("foo")
    step.bar("{{ bar }}")
    step.baz(0)
    step.item("{{ each }}")
    step.foreach('0,1,2')

    def steps = []
    step.eachStep([:], { stepItem ->
      steps << stepItem.clone()
    })
    assert 3 == steps.size()

    assert [foo:'foo', bar:'{{ bar }}', baz:0, item:'0'] == steps[0].args
    assert [foo:'foo', bar:'{{ bar }}', baz:0, item:'1'] == steps[1].args
    assert [foo:'foo', bar:'{{ bar }}', baz:0, item:'2'] == steps[2].args
  }
}

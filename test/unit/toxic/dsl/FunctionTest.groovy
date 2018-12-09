package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class FunctionTest {
  @Test
  void should_compile_simple_function() {
    def input =  { ->
      function "foo", {
        path "foo-path"
        description "foo description"
        targets "bar"

        arg  "required-arg", true
        arg  "optional-arg", false
        arg  "other"

        output "a"
        output "b"
      }
    }

    Parser.parse(new Function(), input).with { functions ->
      assert 1 == functions.size()
      Function fn = functions[0]
      assert fn.path == 'foo-path'
      assert fn.description == 'foo description'
      assert fn.targets == ['bar'] as Set
      assert fn.args[0].name == 'required-arg'
      assert fn.args[0].required == true
      assert fn.args[1].name == 'optional-arg'
      assert fn.args[1].required == false
      assert fn.args[2].name == 'other'
      assert fn.args[2].required == true
      assert fn.outputs == ['a': null, 'b': null]
      assert fn.steps == []
    }
  }

  @Test
  void should_compile_simple_function_from_string() {
    def input = """
      function "foo", {
        path "foo-path"
        description "foo-description"
        targets "bar"

        arg    "required-arg", true
        arg    "optional-arg", false
        input  "other"

        output "a"
        output "b"
      }"""

    Function.parse(input).with { functions ->
      assert 1 == functions.size()
      Function fn = functions[0]
      assert fn.path == 'foo-path'
      assert fn.description == 'foo-description'
      assert fn.args[0].name == 'required-arg'
      assert fn.args[0].required == true
      assert fn.args[1].name == 'optional-arg'
      assert fn.args[1].required == false
      assert fn.args[2].name == 'other'
      assert fn.args[2].required == true
      assert fn.outputs == ['a': null, 'b': null]
      assert fn.targets?.contains('bar')
      assert fn.steps == []
    }
  }

  @Test
  void should_compile_simple_function_without_comma_from_string() {
    def input = """
      function "foo" {
        path "foo-path"
        description "foo-description"

        arg  "required-arg", true
        arg  "optional-arg", false
        arg  "other"

        output "a"
        output "b"
      }"""

    Function.parse(input).with { functions ->
      assert 1 == functions.size()
      Function fn = functions[0]
      assert fn.path == 'foo-path'
      assert fn.description == 'foo-description'
      assert fn.args[0].name == 'required-arg'
      assert fn.args[0].required == true
      assert fn.args[1].name == 'optional-arg'
      assert fn.args[1].required == false
      assert fn.args[2].name == 'other'
      assert fn.args[2].required == true
      assert fn.outputs == ['a': null, 'b': null]
      assert fn.steps == []
    }
  }

  @Test
  void should_compile_multiple_functions_from_string() {
    def input = """
      function "foo", {
        path "foo-path"
        description "foo-description"

        arg  "foo-required-arg", true
        arg  "foo-optional-arg", false
        arg  "foo-other"

        output "foo-a"
        output "foo-b"
      }
      
      function "bar", {
        path "bar-path"
        description "bar-description"

        arg  "bar-required-arg", true
        arg  "bar-optional-arg", false
        arg  "bar-other"

        output "bar-a"
        output "bar-b"
      }"""

    Function.parse(input).with { fns ->
      assert 2 == fns.size()

      Function fn1 = fns[0]
      assert fn1.name == 'foo'
      assert fn1.path == 'foo-path'
      assert fn1.description == 'foo-description'
      assert fn1.args[0].name == 'foo-required-arg'
      assert fn1.args[0].required == true
      assert fn1.args[1].name == 'foo-optional-arg'
      assert fn1.args[1].required == false
      assert fn1.args[2].name == 'foo-other'
      assert fn1.args[2].required == true
      assert fn1.outputs == ['foo-a': null, 'foo-b': null]

      Function fn2 = fns[1]
      assert fn2.name == 'bar'
      assert fn2.path == 'bar-path'
      assert fn2.description == 'bar-description'
      assert fn2.args[0].name == 'bar-required-arg'
      assert fn2.args[0].required == true
      assert fn2.args[1].name == 'bar-optional-arg'
      assert fn2.args[1].required == false
      assert fn2.args[2].name == 'bar-other'
      assert fn2.args[2].required == true
      assert fn2.outputs == ['bar-a': null, 'bar-b': null]
    }
  }

  @Test
  void should_compile_function_with_steps() {
    def input =  { ->
      function "foo", {
        description "foo description"

        input "foo"

        step "Step1", "step1", {
          input1 'value1'
        }
        step "Step2", "step2", {
          input2 '{{ step.step1.output }}'
        }

        output "bar"
      }
    }

    Parser.parse(new Function(), input).with { functions ->
      assert 1 == functions.size()
      Function fn = functions[0]
      assert null == fn.path
      assert fn.description == 'foo description'

      assert 2 == fn.steps.size()
      assert 'step1' == fn.steps[0].name
      assert 'Step1' == fn.steps[0].function
      assert ['input1':'value1'] == fn.steps[0].args

      assert [:] == fn.steps[1].outputs
      assert 'step2' == fn.steps[1].name
      assert 'Step2' == fn.steps[1].function
      assert ['input2':'{{ step.step1.output }}'] == fn.steps[1].args
      assert [:] == fn.steps[1].outputs
    }
  }

  @Test
  void should_compile_function_with_output_values() {
    def input =  { ->
      function "foo", {
        path "foo-path"
        description "foo description"

        output "a", "foo"
        output "b", "{{ bar }}"
      }
    }

    Parser.parse(new Function(), input).with { functions ->
      assert 1 == functions.size()
      Function fn = functions[0]
      assert fn.path == 'foo-path'
      assert fn.description == 'foo description'
      assert fn.outputs == ['a': 'foo', 'b': '{{ bar }}']
      assert fn.steps == []
    }
  }

  @Test
  void should_normalize_dsl() {
    def parser = new Function()
    assert 'function "foo", {' == parser.normalize('function "foo" {')
    assert 'function "foo", {' == parser.normalize('function "foo", {')
    assert 'function "foo", {' == parser.normalize('function "foo"{')
    assert 'function "foo", ' == parser.normalize('function "foo"')
    assert '      function "foo", {' == parser.normalize('      function "foo" {')

    def actualMultiline = """
      function "foo" {
        path "foo-path"

        arg  "required-arg", true
        arg  "optional-arg", false
        arg  "other"

        output "a"
        output "b"
      }"""

    def expectedMultiline = """
      function "foo", {
        path "foo-path"

        arg  "required-arg", true
        arg  "optional-arg", false
        arg  "other"

        output "a"
        output "b"
      }"""
    assert expectedMultiline == parser.normalize(actualMultiline)
  }

  @Test
  void should_validate_required_fields() {
    def assertRequiredField = { Function function, String fields ->
      try {
        function.validate()
        fail('Expected IllegalStateException')
      }
      catch(IllegalStateException e) {
        assert "Missing required fields for function; name=${function.name}; fields=[${fields}]" == e.message
      }
    }

    assertRequiredField(new Function(), 'name, description, (path OR steps)')
    assertRequiredField(new Function('foo'), 'description, (path OR steps)')
    assertRequiredField(new Function(name: 'foo', path: 'path'), 'description')
  }

  @Test
  void should_validate_name() {
    def assertValidName = { Function function ->
      try {
        function.validate()
        fail('Expected IllegalArgumentException')
      }
      catch(IllegalArgumentException e) {
        assert "Function name cannot contain dots; name=${function.name}" == e.message
      }
    }

    assertValidName(new Function(name:'Foo.invalid', description:'N/A', path:'/'))
    assertValidName(new Function(name:'.Foo', description:'N/A', path:'/'))
    assertValidName(new Function(name:'Foo.', description:'N/A', path:'/'))
    assertValidName(new Function(name:'.', description:'N/A', path:'/'))
    assertValidName(new Function(name:'foo.bar.baz', description:'N/A', path:'/'))
  }

  @Test
  void should_validate_path_and_steps_cannot_be_defined_together() {
    try {
      new Function(name: 'foo', description: 'bar', path: 'path', steps: [new Step()]).validate()
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert "Function cannot specify both path and steps; name=foo" == e.message
    }
  }

  @Test
  void should_validate_required_args_are_present_in_props_map() {
    def input = """
      function "foo" {
        path "foo-path"
        description "foo-description"

        arg  "required-arg1", true
        arg  "required-arg2", true
        arg  "optional-arg", false
        arg  "other"
      }"""

    Function function = Function.parse(input)[0]

    def validateRequiredArgsArePresent = { def props, String expectedArgs = '' ->
      try {
        function.validateRequiredArgsArePresent(props)
        return true
      }
      catch(IllegalStateException e) {
        assert "Missing required args for function; name=foo; args=[${expectedArgs}]" == e.message
        return false
      }
    }
    assert false == validateRequiredArgsArePresent([:], 'required-arg1, required-arg2, other')
    assert false == validateRequiredArgsArePresent(['required-arg1':'someValue'], 'required-arg2, other')
    assert true == validateRequiredArgsArePresent(['required-arg1':'someValue1', 'required-arg2':'someValue2', 'other':'someValue3'])
  }

  @Test
  void should_validate_arg_is_defined() {
    def input = """
      function "foo" {
        path "foo-path"
        description "foo-description"

        arg  "required-arg1", true
        arg  "required-arg2", true
        arg  "optional-arg", false
        arg  "other"
      }"""

    Function function = Function.parse(input)[0]

    def validateArgIsDefined = { def arg ->
      try {
        function.validateArgIsDefined(arg)
        return true
      }
      catch(IllegalStateException e) {
        assert "Arg is not defined for function; name=foo; arg=${arg}" == e.message
        return false
      }
    }
    assert true == validateArgIsDefined('required-arg1')
    assert true == validateArgIsDefined('required-arg2')
    assert true == validateArgIsDefined('optional-arg')
    assert true == validateArgIsDefined('other')
    assert false == validateArgIsDefined('not-defined')
  }

  @Test
  void should_set_default_arg_value() {
    def assertArg = { Arg arg, String expectedName, boolean expectedRequired, boolean expectedHasDefaultValue,  def expectedDefaultValue ->
      assert expectedName == arg.name
      assert expectedRequired == arg.required
      assert expectedHasDefaultValue == arg.hasDefaultValue
      assert expectedDefaultValue == arg.defaultValue
    }

    ['arg', 'input'].each {
      def input = """
      function "foo" {
        path "foo-path"
        description "foo-description"

        TOKEN "required-arg1"
        TOKEN "optional-arg-1", false
        TOKEN "optional-arg-2", false, "bar"
        TOKEN "optional-arg-3", false, true
        TOKEN "optional-arg-4", false, 12.3
        TOKEN "optional-arg-5", false, null
      }""".replaceAll('TOKEN', it)

      Function function = Function.parse(input)[0]
      assert 6 == function.args.size()
      assertArg(function.args[0], 'required-arg1', true, false, null)
      assertArg(function.args[1], 'optional-arg-1', false, false, null)
      assertArg(function.args[2], 'optional-arg-2', false, true, 'bar')
      assertArg(function.args[3], 'optional-arg-3', false, true, true)
      assertArg(function.args[4], 'optional-arg-4', false, true, 12.3)
      assertArg(function.args[5], 'optional-arg-5', false, true, null)
    }
  }

  @Test
  void should_not_allow_default_values_on_required_args() {
    def input = """
      function "foo" {
        path "foo-path"
        description "foo-description"

        arg "required-arg1", true, "foo"
      }"""
    try {
      Function.parse(input)
      fail('Expected IllegalStateException')
    }
    catch(IllegalStateException e) {
      assert "Cannot specify a default value on a required arg; name=foo; args=required-arg1; defaultValue=foo" == e.message
    }
  }

  @Test
  void should_determine_has_target() {
    def fn = new Function(targets: ['foo', 'bar'])

    assert true == fn.hasTarget(null)
    assert true == fn.hasTarget('')
    assert true == fn.hasTarget('foo')
    assert true == fn.hasTarget('bar')
    assert false == fn.hasTarget('UNKNOWN')
  }

  @Test
  void should_determine_is_default() {
    assert true == new Function().isDefault()
    assert true == new Function(targets: null).isDefault()
    assert true == new Function(targets: []).isDefault()
    assert false == new Function(targets: ['foo']).isDefault()
  }

  @Test
  void should_compare_two_functions() {
    shouldBeEqual(new Function(), new Function())
    shouldBeEqual(new Function(name: 'foo'), new Function(name: 'foo'))
    shouldBeEqual(new Function(name: 'foo', targets: []), new Function(name: 'foo', targets: []))
    shouldBeEqual(new Function(name: 'foo', targets: []), new Function(name: 'foo', targets: null))
    shouldNotBeEqual(new Function(name: 'foo', targets: ['foo']), new Function(name: 'foo', targets: null))
  }

  private boolean shouldBeEqual(Function a, Function b) {
    assert a == b
  }

  private boolean shouldNotBeEqual(Function a, Function b) {
    assert a != b
  }

  @Test
  void should_override_to_string() {
    Function f1 = new Function(name: 'foo', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    Function f2 = new Function(name: 'bar', args: [new Arg(name: 'arg1')], targets: ['baz'])
    assert 'foo(arg1,arg2) []' == f1.toString()
    assert 'bar(arg1) [baz]' == f2.toString()
  }
}

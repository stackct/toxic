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
      assert fn.args[0].name == 'required-arg'
      assert fn.args[0].required == true
      assert fn.args[1].name == 'optional-arg'
      assert fn.args[1].required == false
      assert fn.args[2].name == 'other'
      assert fn.args[2].required == true
      assert fn.outputs == ['a', 'b'] as Set
    }
  }

  @Test
  void should_compile_simple_function_from_string() {
    def input = """
      function "foo", {
        path "foo-path"
        description "foo-description"

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
      assert fn.outputs == ['a', 'b'] as Set
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
      assert fn.outputs == ['a', 'b'] as Set
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
      assert fn1.outputs == ['foo-a', 'foo-b'] as Set

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
      assert fn2.outputs == ['bar-a', 'bar-b'] as Set
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

    assertRequiredField(new Function(), 'name, path, description')
    assertRequiredField(new Function('foo'), 'path, description')
    assertRequiredField(new Function(name: 'foo', path: 'path'), 'description')
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
  void should_override_to_string() {
    Function function = new Function(name: 'foo', args: [new Arg(name: 'arg1'), new Arg(name: 'arg2')])
    assert 'foo(arg1,arg2)' == function.toString()
  }
}

package toxic

import org.junit.Test
import static org.junit.Assert.fail

class JsonValidatorTest {
  @Test
  void should_compare_matching_json() {
    String expected = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    String actual = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""

    expectSuccess(expected, actual)
  }

  @Test
  void should_compare_matching_json_ignoring_whitespace() {
    String expected = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    String actual = """{
      "topic": "fooTopic",
      "event": "mainEvent",
      "payload": {
        "foo": "foo"
      }
    }"""

    expectSuccess(expected, actual)
  }

  @Test
  void should_compare_matching_json_ignoring_formatting() {
    String expected = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      }
                    }"""
    String actual = """{ "topic": "fooTopic", "event": "mainEvent", "payload": { "foo": "foo" } }"""
    expectSuccess(expected, actual)
  }

  @Test
  void should_succeed_with_variable_assignment() {
    String expected = """{ "topic": {"foo": "%=foo%"} }"""
    String actual = """{ "topic": {"foo": "bar"} }"""
    def props = [:]
    expectSuccess(expected, actual, props)
    assert 'bar' == props.foo

    expected = """{ "topic": {"foo": "FOO %=foo% BAR"} }"""
    actual = """{ "topic": {"foo": "FOO bar BAR"} }"""
    props = [:]
    expectSuccess(expected, actual, props)
    assert 'bar' == props.foo

    expected = """{ "topic": "%=foo%" }"""
    actual = """{ "topic": {"foo": "bar"} }"""
    props = [:]
    expectSuccess(expected, actual, props)
    assert [foo: 'bar'] == props.foo

    expected = """{ "topic": "%=foo=bar%" }"""
    actual = """{ "topic": {"foo": "bar"} }"""
    props = [bar: [foo: 'bar']]
    expectSuccess(expected, actual, props)
    assert [foo: 'bar'] == props.foo
  }

  @Test
  void should_succeed_when_root_maps_are_equal() {
    String expected = """{ "topic": {"foo": "bar"} }"""
    String actual = """{ "topic": {"foo": "bar"} }"""
    expectSuccess(expected, actual)

    expected = """{ "topic": {"foo": {"foo": {"foo": "bar"} } } }"""
    actual = """{ "topic": {"foo": {"foo": {"foo": "bar"} } } }"""
    expectSuccess(expected, actual)
  }

  @Test
  void should_succeed_when_root_lists_are_equal() {
    String expected = """{ "topic": ["foo", "bar"] }"""
    String actual = """{ "topic": ["foo", "bar"] }"""
    expectSuccess(expected, actual)

    expected = """{ "topic": ["foo", ["foo", ["bar"] ] ] }"""
    actual = """{ "topic": ["foo", ["foo", ["bar"] ] ] }"""
    expectSuccess(expected, actual)
  }

  @Test
  void should_support_skip_pattern_validation() {
    String expected = """{ "topic": "%%" }"""
    String actual = """{ "topic": "foo" }"""
    expectSuccess(expected, actual)

    expected = """{ "topic": { "foo": "%%" } }"""
    actual = """{ "topic": {
                    "foo": {
                      "bar": ["bar1", "bar2"]
                    }
                  }
                }
    """
    expectSuccess(expected, actual)

    expected = """{ "topic": "foo%%1" }"""
    actual = """{ "topic": "foobar1" }"""
    expectSuccess(expected, actual)
  }

  @Test
  void should_succeed_with_list_root() {
    String expected = """[ { "topic1": "foo" } ]"""
    String actual = """[ { "topic1": "foo" } ]"""
    expectSuccess(expected, actual)
  }

  @Test
  void should_fail_with_list_root() {
    String expected = """[ { "topic1": "foo" }, { "topic2": "bar" } ]"""
    String actual = """[ { "topic1": "foo" }, { "topic2": "baz" } ]"""
    String message = 'Content mismatch; path=/[1]/topic2; expected=bar; actual=baz\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_expected_json_is_invalid() {
    String expected = """NOT_VALID_JSON"""
    String actual = """{ "topic": "barTopic" }"""
    try {
      new JsonValidator().validate(actual, expected, [:])
      fail('Expected ValidationException')
    }
    catch(ValidationException e) {
      assert e.message.contains('Unable to determine the current character, it is not a string, number, array, or object')
    }
  }

  @Test
  void should_fail_when_actual_json_is_invalid() {
    String expected = """{ "topic": "barTopic" }"""
    String actual = """NOT_VALID_JSON"""
    try {
      new JsonValidator().validate(actual, expected, [:])
      fail('Expected ValidationException')
    }
    catch(ValidationException e) {
      assert e.message.contains('Unable to determine the current character, it is not a string, number, array, or object')
    }
  }

  @Test
  void should_fail_with_skip_pattern_validation() {
    String expected = """{ "topic": "foo%%1" }"""
    String actual = """{ "topic": "foobar2" }"""
    String message = 'Content mismatch; path=/topic; expected=foo%%1; actual=foobar2\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_root_json_value_does_not_match() {
    String expected = """{ "topic": "fooTopic" }"""
    String actual = """{ "topic": "barTopic" }"""
    String message = 'Content mismatch; path=/topic; expected=fooTopic; actual=barTopic\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_root_json_value_is_missing() {
    String expected = """{ "topic": "fooTopic" }"""
    String actual = """{ }"""
    String message = 'Missing expected property; path=/topic\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_root_json_value_is_unexpected() {
    String expected = """{  }"""
    String actual = """{ "topic": "fooTopic" }"""
    String message = 'Unexpected property; path=/topic; value=fooTopic\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_root_lists_are_not_equal() {
    String expected = """{ "topic": [1, 2, 3] }"""
    String actual = """{ "topic": [4, 5, 6] }"""
    def message = ''<<''
    message << 'Content mismatch; path=/topic/[0]; expected=1; actual=4\n'
    message << 'Content mismatch; path=/topic/[1]; expected=2; actual=5\n'
    message << 'Content mismatch; path=/topic/[2]; expected=3; actual=6\n'
    expectValidationFailure(expected, actual, message.toString())

    expected = """{ "topic": [1, 2, 3] }"""
    actual = """{ "topic": [3, 2, 1] }"""
    message = ''<<''
    message << 'Content mismatch; path=/topic/[0]; expected=1; actual=3\n'
    message << 'Content mismatch; path=/topic/[2]; expected=3; actual=1\n'
    expectValidationFailure(expected, actual, message.toString())

    expected = """{ "topic": [1, 2, 3] }"""
    actual = """{ "topic": [1] }"""
    message = 'Content mismatch; path=/topic; expected=[1, 2, 3]; actual=[1]\n'
    expectValidationFailure(expected, actual, message)

    expected = """{ "topic": [1, 2, 3] }"""
    actual = """{ "topic": [1, 2, 3, 4] }"""
    message = 'Content mismatch; path=/topic; expected=[1, 2, 3]; actual=[1, 2, 3, 4]\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_nested_maps_are_not_equal() {
    String expected = """{ "topic": {"foo": "bar"} }"""
    String actual = """{ "topic": {"foo": "baz"} }"""
    String message = 'Content mismatch; path=/topic/foo; expected=bar; actual=baz\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_nested_maps_in_lists_are_not_equal() {
    String expected = """{ "topic": [{"foo": "bar"}, {"foo": "bar"}] }"""
    String actual = """{ "topic": [{"foo": "bar"}, {"foo": "baz"}] }"""
    String message = 'Content mismatch; path=/topic/[1]/foo; expected=bar; actual=baz\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_fail_when_nested_lists_in_lists_are_not_equal() {
    String expected = """{ "topic": [["foo", "bar"], ["foo", "bar"]] }"""
    String actual = """{ "topic": [["foo", "bar"], ["foo", "baz"]] }"""
    String message = 'Content mismatch; path=/topic/[1]/[1]; expected=bar; actual=baz\n'
    expectValidationFailure(expected, actual, message)
  }

  @Test
  void should_support_multiple_failures_at_various_map_levels() {
    String expected = """{ "topic": { "foo": { "foo": { "foo": "bar" } }, "foo1": "bar1" }, "foo": "bar" }"""
    String actual = """{ "topic": { "foo": { "foo": { "foo": "baz" } }, "foo1": "bar2" }, "foo": "baz" }"""
    def message = ''<<''
    message << 'Content mismatch; path=/foo; expected=bar; actual=baz\n'
    message << 'Content mismatch; path=/topic/foo/foo/foo; expected=bar; actual=baz\n'
    message << 'Content mismatch; path=/topic/foo1; expected=bar1; actual=bar2\n'
    expectValidationFailure(expected, actual, message.toString())
  }

  @Test
  void should_support_multiple_failures_at_various_list_levels() {
    String expected = """{ "topic": [ "foo", [ "foo", [ "foo" ] ] ] }"""
    String actual = """{ "topic": [ "bar", [ "bar", [ "bar" ] ] ] }"""
    def message = ''<<''
    message << 'Content mismatch; path=/topic/[0]; expected=foo; actual=bar\n'
    message << 'Content mismatch; path=/topic/[1]/[0]; expected=foo; actual=bar\n'
    message << 'Content mismatch; path=/topic/[1]/[1]/[0]; expected=foo; actual=bar\n'
    expectValidationFailure(expected, actual, message.toString())
  }

  @Test
  void should_fail_assignment_when_content_mismatch() {
    String expected = """{ "topic": "%=foo=bar%" }"""
    String actual = """{ "topic": "foobar" }"""
    def message = ''<<''
    message << 'Content mismatch; path=/topic; expected=foobaz; actual=foobar\n'
    expectValidationFailure(expected, actual, message.toString(), [bar: 'foobaz'])

    expected = """{ "topic": "%=foo=bar%" }"""
    actual = """{ "topic": { "foo": "bar" } }"""
    message = ''<<''
    message << 'Content mismatch; path=/topic; expected=foobaz; actual=[foo:bar]\n'
    expectValidationFailure(expected, actual, message.toString(), [bar: 'foobaz'])
  }

  def expectSuccess = { String expected, String actual, def props=[:] ->
    try {
      new JsonValidator().validate(actual, expected, props)
    }
    catch(ValidationException e) {
      fail(e.message)
    }
  }

  def expectValidationFailure = { String expected, String actual, String message, def props=[:] ->
    try {
      new JsonValidator().validate(actual, expected, props)
      fail('Expected ValidationException')
    }
    catch(ValidationException e) {
      assert e.message == message
    }
  }
}

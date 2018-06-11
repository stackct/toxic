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
  void should_compare_matching_json_with_null_maps() {
    String expected = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      },
                      "empty": null
                    }"""
    String actual = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      },
                      "empty": null
                    }"""

    expectSuccess(expected, actual)
  }

  @Test
  void should_compare_matching_json_with_lists() {
    String expected = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      },
                      "empty": ["foo", null, "bar", true]
                    }"""
    String actual = """{
                      "topic": "fooTopic",
                      "event": "mainEvent",
                      "payload": {
                        "foo": "foo"
                      },
                      "empty": ["foo", null, "bar", true]
                    }"""

    expectSuccess(expected, actual)
  }

  @Test
  void should_compare_matching_json_with_list_with_nulls() {
    expectValidationFailure('[null]', '["foo"]', "Content mismatch; path=/[0]; expected=null; actual=foo\n")
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
  void should_succeed_with_null_variable_assignment() {
    def props = [:] as ToxicProperties
    def val = new JsonValidator()

    String expected = val.normalizeResponse('{ "foo": %=bar% }')
    String actual   = '{ "foo": null }'

    expectSuccess(expected, actual, props)

    assert props.bar == null
  }

  @Test
  void should_succeed_with_empty_string_variable_assignment() {
    String expected = '{ "foo": "%=bar%" }'
    String actual   = '{ "foo": "" }'

    def props = [:]
    expectSuccess(expected, actual, props)

    assert props.bar == ''
  }

  @Test
  void should_succeed_with_string_null_variable_assignment() {
    String expected = '{ "foo": "%=bar%" }'
    String actual   = '{ "foo": "null" }'

    def props = [:]
    expectSuccess(expected, actual, props)

    assert props.bar == 'null'
  }

  @Test
  void should_succeed_with_multiline_list_variable_assignment() {
    String expected = """[
  { "foo1": "bar1"},
  %=foo2%,
  { "foo3": "bar3"}
]"""
    String actual   = """[
  { "foo1": "bar1"},
  { "foo2": "bar2"},
  { "foo3": "bar3"}
]"""

    def props = new ToxicProperties()
    expectSuccess(JsonValidator.normalizeResponse(expected), actual, props)

    assert props.foo2 == [foo2: 'bar2']
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
  void should_succeed_with_skip_variable() {
    def props = [:] as ToxicProperties
    def val = new JsonValidator()

    String expected = val.normalizeResponse('{ "foo": %% }')
    String actual   = '{ "foo": [] }'

    expectSuccess(expected, actual, props)
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

  @Test
  void should_normalize_request_list_of_maps() {
    String expected = """[
  {"foo1":"bar1"},
  {"foo2":"bar2"},
  {"foo3":"bar3"}
]"""
    String actual   = """[
  {"foo1":"bar1"},
  %foo2%,
  {"foo3":"bar3"}
]"""
    ToxicProperties props = new ToxicProperties()
    props.foo2 = [foo2: 'bar2']
    assert expected == JsonValidator.normalizeRequest(actual, props)
  }

  @Test
  void should_not_normalize_request_with_surrounding_spaces() {
    String expected = """[
  {"foo1":"bar1"},
  {"foo2":"  %foo2%  "},
  {"foo3":"bar3"}
]"""
    ToxicProperties props = new ToxicProperties()
    props.foo2 = [foo2: 'bar2']
    assert expected == JsonValidator.normalizeRequest(expected, props)
  }

  @Test
  void should_normalize_response_list_of_maps() {
    String expected = """[
  { "foo1": "bar1"},
  "%=foo2%",
  { "foo3": "bar3"}
]"""
    String actual   = """[
  { "foo1": "bar1"},
  %=foo2%,
  { "foo3": "bar3"}
]"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_normalize_response_list_of_maps_with_normalized_ignored_value() {
    String expected = """[
  { "foo1": "bar1"},
  "%=foo2%",
  { "foo3": "%%"}
]"""
    String actual   = """[
  { "foo1": "bar1"},
  %=foo2%,
  { "foo3": "%%"}
]"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_normalize_response_list_of_maps_with_non_normalized_ignored_value() {
    String expected = """[
  { "foo1": "bar1"},
  "%=foo2%",
  { "foo3": "%%"}
]"""
    String actual   = """[
  { "foo1": "bar1"},
  %=foo2%,
  { "foo3": %%}
]"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_normalize_ignored_structure_non_normalized_ignored_value() {
    String expected = """[
  { "foo1": "bar1"},
  "%foo2%",
  { "foo3": "%%"}
]"""
    String actual   = """[
  { "foo1": "bar1"},
  %foo2%,
  { "foo3": %%}
]"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_normalize_response_with_ignored_structure() {
    String expected = """{
  "foo": "bar",
  "baz": "%%"
}"""
    String actual   = """{
  "foo": "bar",
  "baz": %%
}"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_normalize_response_with_ignored_structure_and_normalized_string() {
    String expected = """{
  "foo": "bar",
  "baz": "%%",
  "foobaz": "%%"
}"""
    String actual   = """{
  "foo": "bar",
  "baz": %%,
  "foobaz": "%%"
}"""
    assert expected == JsonValidator.normalizeResponse(actual)
  }

  @Test
  void should_not_normalize_response_with_multiple_ignore_all_on_single_line() {
    String expected = """{
  "foo": "bar",
  "baz": "%%//%%/path/to/somewhere"
}"""
    assert expected == JsonValidator.normalizeResponse(expected)
  }

  @Test
  void should_not_normalize_response_with_multiple_ignore_all_on_single_line_with_spaces() {
    String expected = """{
  "foo": "bar",
  "baz": "   %%  %%      "
}"""
    assert expected == JsonValidator.normalizeResponse(expected)
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
      assert new JsonValidator().formatValidatorExceptionMessage(expected, actual, message) == e.message
    }
  }
}

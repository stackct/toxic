package toxic

import groovy.json.JsonException
import groovy.json.JsonSlurper

class JsonValidator extends TextValidator {
  @Override
  void validate(def actualJson, def expectedJson, def memory) {
    def expected
    def actual

    try {
      def (actualIdx, expectedIdx) = startingIndexes(actualJson, expectedJson)

      expected = new JsonSlurper().parseText(expectedJson)
      actual = new JsonSlurper().parseText(actualJson[actualIdx..-1])
    }
    catch(JsonException e) {
      throw new ValidationException(e.message)
    }

    def failures = ''<<''
    String path = ''

    validate(expected, actual, failures, path, memory)

    if(failures) {
      throw new ValidationException(failures.toString())
    }
  }

  void validate(Map expected, Map actual, def failures, String basePath, def memory) {
    (expected.keySet() + actual.keySet()).each { k ->
      String path = "${basePath}/${k}"
      if(!expected.containsKey(k)) {
        failures << "Unexpected property; path=${path}; value=${actual[k]}\n"
      }
      else if(!actual.containsKey(k)) {
        failures << "Missing expected property; path=${path}\n"
      }
      else {
        validate(expected[k], actual[k], failures, path, memory)
      }
    }
  }

  void validate(List expected, List actual, def failures, String basePath, def memory) {
    if(expected.size() != actual.size()) {
      failures << mismatchFailure(expected, actual, basePath)
    }
    else {
      expected.eachWithIndex { k, index ->
        String path = "${basePath}/[${index}]"
        validate(expected[index], actual[index], failures, path, memory)
      }
    }
  }

  void validate(String expected, String actual, def failures, String path, def memory) {
    try {
      super.validate(actual, expected, memory)
    }
    catch(ContentMismatchException e) {
      failures << mismatchFailure(e.expected, e.actual, path)
    }
    catch(ValidationException e) {
      failures << mismatchFailure(expected, actual, path)
    }
  }

  void validate(def expected, def actual, def failures, String path, def memory) {
    if(skipValidation(expected)) { return }
    if(isVariableAssignment(expected)) {
      try {
        performVariableAssignment(expected, actual, memory)
      }
      catch(ContentMismatchException e) {
        failures << mismatchFailure(e.expected, e.actual, path)
      }
    }
    else if(expected != actual) {
      failures << mismatchFailure(expected, actual, path)
    }
  }

  String mismatchFailure(def expected, def actual, String path) {
    "Content mismatch; path=${path}; expected=${expected}; actual=${actual}\n"
  }
}

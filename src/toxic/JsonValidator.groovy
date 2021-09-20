package toxic

import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class JsonValidator extends HttpValidator {
  @Override
  void validate(def actualJson, def expectedJson, def memory) {
    if (!expectedJson) return

    def expected
    def actual

    try {
      def (actualIdx, expectedIdx) = startingIndexes(actualJson, expectedJson)

      expected = new JsonSlurper().parseText(expectedJson)
      actual = new JsonSlurper().parseText(actualJson[actualIdx..-1])
    }
    catch(JsonException e) {
      throw new ValidationException(formatValidatorExceptionMessage(expectedJson, actualJson, e.message))
    }

    def failures = ''<<''
    String path = ''

    validate(expected, actual, failures, path, memory)

    if(failures) {
      throw new ValidationException(formatValidatorExceptionMessage(expectedJson, actualJson, failures.toString()))
    }
  }

  String formatValidatorExceptionMessage(String expectedJson, String actualJson, String failures) {
    failures +=
      "\n${'-'.multiply(25)}  ACTUAL  ${'-'.multiply(25)}\n" +
          actualJson + "\n" +
      "\n${'-'.multiply(25)}  EXPECTED  ${'-'.multiply(25)}\n" +
          expectedJson

    return failures
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
      else if (hasContent(expected[k], actual[k])) {
        validate(expected[k], actual[k], failures, path, memory)
      }
    }
  }

  void validate(List expected, List actual, def failures, String basePath, def memory) {

    if(expected?.size() != actual?.size()) {
      failures << mismatchFailure(expected, actual, basePath)
    }
    else {
      expected?.eachWithIndex { k, index ->
        if (hasContent(k, actual[index])) {
          String path = "${basePath}/[${index}]"
          validate(k, actual[index], failures, path, memory)
        }
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
    if(skipValidation(expected)) {
      return
    }

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

  /* Any unquoted variables, such as map or list references, will be replaced
     with the String representation of the JSON structure.
  */
  static String normalizeRequest(String s, ToxicProperties props) {
    def regexes = []
    // Match from a newline, any number of spaces, until an unquoted % is found, capture a variable until a close percent without a trailing quote
    regexes << /((?<=\n)\s*[^"])(%)([^%]+)(%)([^"])/
    // Match a colon followed by any number of spaces, until an unquoted % is found, capture a variable until a close percent without a trailing quote
    regexes << /(:\s*[^"])(%)([^%]+)(%)([^"])/
    // Support req.json files with single %json% in the file.
    regexes << /^(\s*)(%)([^%]+)(%)(\s*)$/

    regexes.each {
      s = s.replaceAll(it) { all, begin, openDelimiter, variable, closeDelimiter, end ->
        "${begin}${JsonOutput.toJson(props[variable])}${end}"
      }
    }
    return s
  }

  /* Quote any unquoted response assignment and ignore variables so contents can be correctly
     parsed as JSON
  */
  static String normalizeResponse(String s) {
    def regexes = []
    // Match from a newline, any number of spaces, until an unquoted % is found, to a close percent without a trailing quote
    regexes << /((?<=\n)\s*[^"])(%[^%]*%)([^"])/
    // Match a colon followed by any number of spaces, until an unquoted % is found, capture a variable until a close percent without a trailing quote
    regexes << /(:\s*[^"])(%=[^%]+%)([^"])/
    // Match a colon followed by any number of spaces, until an unquoted %% is found
    regexes << /(:\s*[^"])(%%)([^"])/

    regexes.each {
      s = s.replaceAll(it) { all, begin, match, end ->
        "${begin}\"${match}\"${end}"
      }
    }
    return s
  }

  boolean hasContent(expected, actual) {
    expected != null || actual != null
  }

  String mismatchFailure(def expected, def actual, String path) {
    "Content mismatch; path=${path}; expected=${expected}; actual=${actual}\n"
  }

  @Override
  protected def nullHandler(def value) {
    return value
  }

  @Override
  protected prepareText(String actualOrig, String expectedOrig) {
    [actualOrig, expectedOrig]
  }
}

package toxic.json

import groovy.json.JsonOutput
import toxic.JsonValidator
import toxic.ToxicProperties
import toxic.http.HttpTask

class JsonTask extends HttpTask {
  @Override
  def prepare(def request) {
    // Any unquoted variables, such as map or list references, will be replaced with the String representation of the JSON structure
    request = request.replaceAll(/(:\s*[^"])(%)([^%]+)(%)([^"])/) { all, begin, openDelimiter, match, closeDelimiter, end ->
      def value = props[match]
      "${begin}${JsonOutput.toJson(value)}${end}"
    }
    
    super.prepare(request)
  }

  @Override
  String lookupExpectedResponse(File file) {
    String responseJson = super.lookupExpectedResponse(file)
    // Quote any unquoted response assignment variables so contents can be correctly parsed as JSON
    responseJson = responseJson.replaceAll(/(:\s*[^"])(%=[^%]+%)([^"])/) { all, begin, match, end ->
      "${begin}\"${match}\"${end}"
    }
  }

  @Override
  void validate(String actualResponse, String expectedResponse, ToxicProperties memory) {
    def validator = new JsonValidator()
    validator.init(memory)
    validator.validate(actualResponse, expectedResponse, memory)
  }
}

package toxic.json

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import toxic.CompareTask
import toxic.JsonValidator
import toxic.TaskResult

class JsonTask extends CompareTask {
  @Override
  List<TaskResult> doTask(Object memory) {
    def request = parseRequestFile()
    String expectedResponse = lookupExpectedResponse(input)
    String actualResponse = sendJsonRequest(memory, request, parseResponseJson(expectedResponse))
    def validator = new JsonValidator()
    validator.init(memory)
    validator.validate(actualResponse, expectedResponse, memory)
    return null
  }

  String sendJsonRequest(Object memory, def request, def expectedResponses) {
    // TODO HTTP post with json
  }

  def parseRequestFile() {
    String requestJson = reqContent
    // Any unquoted variables, such as map or list references, will be replaced with the String representation of the JSON structure
    requestJson = requestJson.replaceAll(/(:\s*[^"])(%)([^%]+)(%)([^"])/) { all, begin, openDelimiter, match, closeDelimiter, end ->
      def value = props[match]
      "${begin}${JsonOutput.toJson(value)}${end}"
    }
    requestJson = prepare(requestJson)
    new JsonSlurper().parseText(requestJson)
  }

  def parseResponseJson(responseJson) {
    new JsonSlurper().parseText(responseJson)
  }

  @Override
  String lookupExpectedResponse(File file) {
    String responseJson = super.lookupExpectedResponse(file)
    // Quote any unquoted response assignment variables so contents can be correctly parsed as JSON
    responseJson.replaceAll(/(:\s*[^"])(%=[^%]+%)([^"])/) { all, begin, match, end ->
      "${begin}\"${match}\"${end}"
    }
  }

  @Override
  protected transmit(Object request, Object memory) { null }
}

package toxic.websocket

import toxic.CompareTask
import toxic.JsonValidator
import toxic.TaskResult
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketTask extends CompareTask {
  private static final long DEFAULT_TIMEOUT = 3000

  @Override
  List<TaskResult> doTask(Object memory) {
    def request = parseRequestFile()
    String expectedResponse = lookupExpectedResponse(input)
    def expectedResponses = parseResponseJson(expectedResponse)
    def channels = collectChannels(request, expectedResponses)

    WebSocketClientEndpoint endpoint = resolveWebSocketClientEndpoint(memory)
    def (latch, actualResponses) = addEventHandlers(endpoint, expectedResponses)

    try {
      endpoint.connect()
      endpoint.join(channels)
      endpoint.sendJsonMessage(request.topic, request.event, request.payload)
      if(!latch.await(latchTimeoutMs(memory), TimeUnit.MILLISECONDS)) {
        throw new WebSocketTimeoutException("Timed out while waiting for response")
      }
    }
    finally {
      endpoint.close()
    }

    def actualResponse = actualResponseString(actualResponses)

    def validator = new JsonValidator()
    validator.init(memory)
    validator.validate(actualResponse, expectedResponse, memory)
    return null
  }

  WebSocketClientEndpoint resolveWebSocketClientEndpoint(def memory) {
    if(memory.webSocketClientEndpoint instanceof WebSocketClientEndpoint) {
      return memory.webSocketClientEndpoint
    }
    if(!memory.webSocketClientUri) {
      throw new IllegalStateException('Please configure either a webSocketClientUri or webSocketClientEndpoint property')
    }
    if('phoenix' == memory.webSocketClientType) {
      return new PhoenixWebSocketClientEndpoint(memory.webSocketClientUri)
    }
    return new WebSocketClientEndpoint(memory.webSocketClientUri)
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

  @Override
  String lookupExpectedResponse(File file) {
    String responseJson = super.lookupExpectedResponse(file)
    // Quote any unquoted response assignment variables so contents can be correctly parsed as JSON
    responseJson.replaceAll(/(:\s*[^"])(%=[^%]+%)([^"])/) { all, begin, match, end ->
      "${begin}\"${match}\"${end}"
    }
  }

  def parseResponseJson(responseJson) {
    new JsonSlurper().parseText(responseJson)
  }

  def collectChannels(def request, def responses) {
    def channels = [request.topic] as Set
    responses.each { channels << it.topic }
    channels
  }

  Tuple addEventHandlers(WebSocketClientEndpoint endpoint, def expectedResponses) {
    def latch = new CountDownLatch(expectedResponses.size())
    def actualResponses = new ConcurrentHashMap()

    expectedResponses.eachWithIndex { response, index ->
      endpoint.addEventHandler(response.topic, response.event) { actualPayload ->
        actualResponses.put(index, [topic: response.topic, event: response.event, payload: actualPayload])
        latch.countDown()
      }
    }
    new Tuple(latch, actualResponses)
  }

  String actualResponseString(def responses) {
    def actualResponse = ''<<''
    actualResponse << '['
    responses.sort()*.key.each { responseIndex ->
      actualResponse << JsonOutput.toJson(responses[responseIndex])
      if(responseIndex < responses.size()-1) {
        actualResponse << ','
      }
    }
    actualResponse << ']'
    actualResponse.toString()
  }

  long latchTimeoutMs(def memory) {
    if(memory.webSocketClientTimeoutMs instanceof Integer || memory.webSocketClientTimeoutMs instanceof Long) {
      return memory.webSocketClientTimeoutMs
    }
    DEFAULT_TIMEOUT
  }

  @Override
  protected transmit(Object request, Object memory) { null }
}

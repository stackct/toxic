package toxic.websocket

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import toxic.json.JsonTask

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebSocketTask extends JsonTask {
  private static final long DEFAULT_TIMEOUT = 3000

  @Override
  protected String transmit(requestString, expectedResponse, memory) {
    def request = new JsonSlurper().parseText(requestString)
    def expectedResponses = new JsonSlurper().parseText(expectedResponse)
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

    return actualResponseString(actualResponses)
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
}

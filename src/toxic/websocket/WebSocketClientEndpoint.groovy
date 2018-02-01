package toxic.websocket

import log.Log
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import javax.websocket.ClientEndpoint
import javax.websocket.CloseReason
import javax.websocket.ContainerProvider
import javax.websocket.OnClose
import javax.websocket.OnMessage
import javax.websocket.OnOpen
import javax.websocket.RemoteEndpoint
import javax.websocket.Session
import javax.websocket.WebSocketContainer

@ClientEndpoint
class WebSocketClientEndpoint {
  private final static Log log = Log.getLogger(this)
  URI endpointURI
  Session userSession
  def messageHandler
  def eventHandlers = [:]

  void join(def channels) {}

  WebSocketClientEndpoint(String endpointURI) {
    this.endpointURI = URI.create(endpointURI)
    configureJsonResponseHandler()
  }

  WebSocketClientEndpoint configureJsonResponseHandler() {
    this.messageHandler = { String message ->
      def json = new JsonSlurper().parseText(message)
      handleResponse(json.topic, json.event, json.payload)
    }
    this
  }

  WebSocketContainer getContainerProvider() {
    ContainerProvider.getWebSocketContainer()
  }

  void connect() {
    getContainerProvider().connectToServer(this, endpointURI)
  }

  void close() {
    userSession?.close()
  }

  void addEventHandler(String topic, String event, Closure c) {
    if(!eventHandlers.containsKey(topic)) {
      eventHandlers[topic] = [:]
    }
    if(eventHandlers[topic].containsKey(event)) {
      throw new IllegalStateException("Duplicate event handlers defined for same topic; topic=${topic}; event=${event}")
    }
    eventHandlers[topic][event] = c
  }

  /**
   * Callback hook for Connection open events.
   *
   * @param userSession the userSession which is opened.
   */
  @OnOpen
  void onOpen(Session userSession) {
    log.debug('opening websocket')
    this.userSession = userSession
  }

  /**
   * Callback hook for Connection close events.
   *
   * @param userSession the userSession which is getting closed.
   * @param reason the reason for connection close
   */
  @OnClose
  void onClose(Session userSession, CloseReason reason) {
    log.debug('closing websocket')
    this.userSession = null
  }

  /**
   * Callback hook for Message Events. This method will be invoked when a client send a message.
   *
   * @param message The text message
   */
  @OnMessage
  void onMessage(String message) {
    if (messageHandler) {
      messageHandler(message)
    }
  }

  void handleResponse(String topic, String event, def payload) {
    def handler = eventHandlers."${topic}"?."${event}"
    if(!handler) {
      throw new UnexpectedResponseException("No response handler defined for event; topic=${topic}; event=${event}; payload=${payload}")
    }
    handler(payload)
  }

  void sendMessage(String message) {
    remoteEndpoint().sendText(message)
  }

  void sendJsonMessage(String topic, String event, Map payload = [:]) {
    sendMessage(JsonOutput.toJson(messageMap(topic, event, JsonOutput.toJson(payload))))
  }

  def messageMap(String topic, String event, String payload) {
    [topic: topic, event: event, payload: payload]
  }

  RemoteEndpoint.Async remoteEndpoint() {
    this.userSession.getAsyncRemote()
  }
}

package toxic.websocket

import javax.websocket.ClientEndpoint

@ClientEndpoint
class PhoenixWebSocketClientEndpoint extends WebSocketClientEndpoint {
  PhoenixWebSocketClientEndpoint(String endpointURI) {
    super(endpointURI)
  }

  @Override
  void join(def channels) {
    channels.each { topic ->
      addEventHandler(topic, 'phx_reply', {def message ->})
      sendJsonMessage(topic, 'phx_join')
    }
  }

  @Override
  def messageMap(String topic, String event, String payload) {
    [topic: topic, event: event, payload: payload, ref: UUID.randomUUID().toString()]
  }
}

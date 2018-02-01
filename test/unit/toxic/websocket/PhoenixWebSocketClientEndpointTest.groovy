package toxic.websocket

import org.junit.Test

class PhoenixWebSocketClientEndpointTest {
  String uri = 'ws://localhost:4000'

  @Test
  void should_construct() {
    PhoenixWebSocketClientEndpoint endpoint = new PhoenixWebSocketClientEndpoint(uri)
    assert uri == endpoint.endpointURI.toString()
  }

  @Test
  void should_join_channels() {
    def topics = ['topic1', 'topic2', 'topic3']
    def actualMessages = []
    PhoenixWebSocketClientEndpoint endpoint = new PhoenixWebSocketClientEndpoint(uri)
    endpoint.metaClass.sendJsonMessage = { String topic, String event, Map payload = [:] ->
      actualMessages << [topic: topic, event: event, payload: payload]
    }
    endpoint.join(topics)

    def handlers = endpoint.eventHandlers
    assert 3 == handlers.size()
    handlers.eachWithIndex { item, index ->
      def topic = item.key
      assert topics[index] == topic

      def eventHandlers = item.value
      assert 1 == eventHandlers.size()

      assert ['phx_reply'] == eventHandlers.keySet() as String[]

      assert null == eventHandlers['phx_reply']('mockpayload')
    }

    assert 3 == actualMessages.size()
    topics.eachWithIndex { topic, index ->
      def message = actualMessages[index]
      assert topic == message.topic
      assert 'phx_join' == message.event
      assert [:] == message.payload
    }
  }

  @Test
  void should_implement_custom_message_map() {
    PhoenixWebSocketClientEndpoint endpoint = new PhoenixWebSocketClientEndpoint(uri)
    def messageMap = endpoint.messageMap('topic1', 'event1', '{foo: "far"}')
    assert 4 == messageMap.size()
    assert 'topic1' == messageMap.topic
    assert 'event1' == messageMap.event
    assert '{foo: "far"}' == messageMap.payload
    assert null != messageMap.ref
  }
}

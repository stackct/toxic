package toxic.slack

import org.junit.*
import toxic.*
import toxic.job.*
import toxic.slack.*

class SlackBotTest {
  @After
  void after() {
    SlackBot.metaClass = null
    SlackBot.instance.metaClass = null
  }
  
  @Test
  void should_create() {
    def old = SlackBot.instance
    assert SlackBot.instance == old
  }
  
  @Test
  void should_process_message() {
    def bot = SlackBot.instance
    def sent
    bot.metaClass.send = { def msg -> sent = msg }
    def response = ""
    bot.handler = new SlackHandler() { 
      Object config(Object key) { return key }
      String handleCommand(SlackBot b, def msg) { return response }
    }
    bot.rtm = [self:[id:"test"]]
    
    def msg = [text:"something",channel:'ch1']
    response = "msg1"
    bot.processMessage_message(msg)
    assert sent.text == response

    msg = [text:"<@test> hi",channel:'ch1']
    bot.processMessage_message(msg)
    assert sent.text == response
    assert sent.channel == 'ch1'
    assert sent.type == "message"

    msg = [text:"<@test>: hi",channel:'ch1']
    bot.processMessage_message(msg)
    assert sent.text == response
    assert sent.channel == 'ch1'
    assert sent.type == "message"
  }
  
  @Test
  void should_locate_token_in_config() {
    def props = [:]

    def bot = SlackBot.instance
    bot.handler = new SlackHandler() { 
      Object config(Object key) { return props[key] }
      String handleCommand(SlackBot b, def msg) { return response }
    }

    assert !bot.shouldReconnect()

    props['secure.slack.token'] = '123'
    bot.wss = new Object() { def isOpen() { return true }}
    assert bot.shouldReconnect()
  }
  
  @Test
  void should_send_to_channels() {
    def bot = SlackBot.instance
    bot.rtm = [channels:[[id:'id1',name:'ch1',is_member:true],[id:'id2',name:'ch2',is_member:false]],groups:[[id:'gd1',name:'gr1',is_group:true],[id:'gd1',name:'gr2',is_group:true]]]
    def sent = []
    def reconnect = false
    bot.metaClass.send = { def msg -> sent << msg }
    bot.metaClass.shouldReconnect = { -> reconnect }

    assert !bot.sendMessageToChannels("ch3", "msg1")
    assert !sent
    
    sent = []
    assert bot.sendMessageToChannels("ch1,ch2", "msg1")
    assert sent.size() == 1
    assert sent[0].text == "msg1"
    assert sent[0].channel == "id1"

    sent = []
    assert bot.sendMessageToChannels("ch1,gr1", "msg1")
    assert sent.size() == 2
    assert sent[0].text == "msg1"
    assert sent[0].channel == "id1"
    assert sent[1].text == "msg1"
    assert sent[1].channel == "gd1"

    sent = []
    reconnect = true
    assert !bot.sendMessageToChannels("ch1", "msg1")
    assert !sent
  }

  @Test
  void should_send_to_users() {
    def bot = SlackBot.instance
    bot.rtm = [channels:[],groups:[],users:[[id:'id1',name:'us1',profile:[email:'em1']],[id:'id2',name:'us2',real_name:'rn2']]]
    def sent = []
    def reconnect = false
    bot.metaClass.send = { def msg -> sent << msg }
    bot.metaClass.shouldReconnect = { -> reconnect }
    bot.metaClass.post = { url, args -> [channel:[id:'id1']] }
    assert !bot.sendMessageToUsers("us3", "msg1")
    assert !sent
    
    sent = []
    assert bot.sendMessageToUsers("us1,id2", "msg1")
    assert sent.size() == 2
    assert sent[0].text == "msg1"
    assert sent[0].channel == "id1"
    assert sent[1].text == "msg1"
    assert sent[1].channel == "id1"
  }

  @Test
  void should_find_user() {
    def bot = SlackBot.instance
    bot.rtm = [users:[[id:'id1',name:'us1',profile:[email:'em1']],[id:'id2',name:'us2',real_name:'rn2']]]

    assert bot.find('id1').name == 'us1'
    assert bot.find('us1').id == 'id1'
    assert bot.find(null, [name: 'rn1', email:'em1']).id == 'id1'
    assert bot.find(null, [name: null,  email:'em1']).id == 'id1'
    assert bot.find(null, [name: 'rn2', email:'em2']).id == 'id2'
  }

  @Test
  void should_create_im() {
    def postedUrl
    def postedArgs
    def bot = SlackBot.instance
    bot.metaClass.post = { url, args ->
      postedUrl = url
      postedArgs = args
    }
    bot.rtm = [users:[[id:'id1',name:'us1',profile:[email:'em1']],[id:'id2',name:'us2',real_name:'rn2']]]
    bot.createIMChannel("us1")
    assert postedUrl == "im.open"
    assert postedArgs.user == 'id1'
  }
  
  @Test
  void should_silence() {
    SlackBot.instance.silenceUntil(0)
    assert !SlackBot.instance.isSilenced()
    SlackBot.instance.silenceUntil(System.currentTimeMillis() + 10000)
    assert SlackBot.instance.isSilenced()
    SlackBot.instance.silenceUntil(System.currentTimeMillis() - 10000)
    assert !SlackBot.instance.isSilenced()
  }

  @Test
  void shouldGracefullyHandleConnectException() {
    SlackBot.instance.metaClass.connect = { -> throw new Exception("uh oh") }
    SlackBot.instance.metaClass.shouldReconnect = { -> true }
    SlackBot.instance.attemptConnection()
    // Test passes if no exception is thrown from this test method
  }
}
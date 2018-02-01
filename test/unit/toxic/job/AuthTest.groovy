package toxic.job

import org.junit.*
import toxic.slack.SlackBot

class AuthTest {
  def token = "hi|123|bye".bytes.encodeBase64().toString()
  def sentSlack = 0
  def slackProps
  def slackTemplateId
  def slackAuth
  def slackAttrs
  def slackResponse = false
  def configData = [:]
  
  @Before
  void before() {
    Auth.metaClass.'static'.getCipher = { p, boolean m -> [doFinal:{i->i}] }
    Auth.metaClass.'static'.sendSlack = { props, templateId, auth, attrs -> 
      slackProps = props
      slackTemplateId = templateId
      slackAuth = auth
      slackAttrs = attrs
      sentSlack++ 
      return slackResponse
    }
    ConfigManager.instance.metaClass.write = { String configDir, String block, Map data -> 
      configData[block] = data
    }
    Auth.log.level = org.apache.log4j.Level.DEBUG
    Date.metaClass.getTime = { return 123 }    
  }
  
  @After
  void after() {
    Auth.metaClass = null
    Date.metaClass = null
    SlackBot.instance.metaClass = null
    ConfigManager.instance.metaClass = null
  }
  
  @Test
  public void should_exp() {
    def props = [:]
    Auth.getAuthExp(props) == 600000
    Auth.getSessionExp(props) == 604800000
    
    props.authTokenExpirationMs = "12"
    props.sessionTokenExpirationMs = "34"
    Auth.getAuthExp(props) == 12
    Auth.getSessionExp(props) == 34
  }
  
  @Test
  public void should_generate_token() {
    def actual = Auth.generateToken(null,"hi","bye")
    assert token == actual
  }

  @Test
  public void should_validate_token() {
    def auth = Auth.validateToken(null,"hi",token,1)
    assert auth == "bye"
  }

  @Test
  public void should_return_user_object_with_validated_user() {
    Auth.metaClass.'static'.validateToken = { p, tp, tk, e -> true }

    SlackBot.instance.metaClass.findUser = { user, name, email -> 
      [profile: [image_72: 'image', first_name: 'john', last_name: 'doe', real_name: 'john doe']]
    }
    
    def cookieKey
    def cookieValue
    def cookieExp
    
    def req = new Object(){}
    def resp = new Object() {
      def addCookie(cookie) {
        cookieKey = cookie.name
        cookieValue = cookie.value
        cookieExp = cookie.maxAge
      }
    }

    Auth.validate([:], req, resp, token).with { result ->
      assert result['auth'] == true
      assert result['token'] == 'c2Vzc2lvbnwxMjN8dHJ1ZQ=='
      assert result['expiration'] == 604800123
      assert cookieKey == 'authtoken'
      assert cookieValue == 'c2Vzc2lvbnwxMjN8dHJ1ZQ=='
      assert cookieExp == 604800
    }
  }

  @Test
  public void should_add_user_on_successful_identification() {
    SlackBot.instance.metaClass.find = { String user -> 
      [id:'W1234566', profile: [image_72: 'image', first_name: 'john', last_name: 'doe', real_name: 'john doe']]
    }

    def req = new Object() {
      def getCookies() { return [[name:'authtoken',value:'ctoken']] }
      def getRemoteHost() { "test.host"}
      def getHeader(String key) { null }
    }
    def resp = new Object() {}
    
    def foundToken
    Auth.metaClass.'static'.validateToken = { p, tp, tk, e -> foundToken = tk }

    Auth.identify([:], req, resp, 'token')
    assert foundToken == 'token'
    
    Auth.identify([:], req, resp, null) 
    assert foundToken == 'ctoken'
    assert configData
  }

  @Test
  public void should_return_false_with_invalid_user() {
    Auth.metaClass.'static'.validateToken = { p, tp, tk, e -> false }

    assert Auth.validate([:], "foo", [:], token) == false
  }

  @Test
  public void should_send_token() {
    def props = [a:1]
    Auth.sendToken(props,"bye",token,"slack","http://ok")
    assert sentSlack == 1
    assert slackProps.is(props)
    assert slackTemplateId == "auth-request"
    assert slackAuth == "bye"
    assert slackAttrs.url == "http://ok?token=aGl8MTIzfGJ5ZQ%3D%3D"
  }
  
  @Test
  public void should_request() {
    def result = Auth.request(null, null, null, "bye", "slack", "http://ok")
    assert !result

    slackResponse = true
    result = Auth.request(null, null, null, "bye", "slack", "http://ok")
    assert result.auth == true

    result = Auth.request(null, null, null, "bye", "other", "http://ok")
    assert !result
  }
  
  @Test
  public void should_identify() {
    
    SlackBot.instance.metaClass.find = { String user -> 
      [id:'W1234566', profile: [image_72: 'image', first_name: 'john', last_name: 'doe', real_name: 'john doe']]
    }

    def req = new Object() {
      def getCookies() { return [[name:'authtoken',value:'ctoken']] }
      def getRemoteHost() { "test.host"}
      def getHeader(String key) { null }
    }
    def resp = new Object() {}
    
    def foundToken
    Auth.metaClass.'static'.validateToken = { p, tp, tk, e -> foundToken = tk }

    Auth.identify([:], req, resp, 'token')
    assert foundToken == 'token'
    
    Auth.identify([:], req, resp, null) 
    assert foundToken == 'ctoken'
  }
    
  @Test
  public void should_identify_by_x_user_header() {
    SlackBot.instance.metaClass.find = { String user -> 
      [id:'W1234567', profile: [image_72: 'image', first_name: 'john', last_name: 'doe', real_name: 'john doe']]
    }

    def req = new Object() {
      def getRemoteHost() { "test.host"}
      def getHeader(String key) { key == 'X-User' ? "sample" : '1.2.3.4'}
    }
    def resp = new Object() {}
    
    def data = Auth.identify([:], req, resp, 'token')
    assert data.authenticated == true
    assert data.auth == '1.2.3.4'
  }
    
}
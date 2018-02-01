package email;

import static org.junit.Assert.*
import groovy.mock.interceptor.*

import javax.activation.*
import javax.mail.*
import javax.mail.internet.*
import com.sun.mail.smtp.*

import org.apache.log4j.Level
import org.junit.*
import org.junit.internal.runners.statements.*

import template.*

class SmtpServiceTest {
  @org.junit.BeforeClass static void beforeClass() { log.Log.configureSimpleLogging() }
  def sent = []
  def aborts
  
  @Before
  void before() {
    reset()
    SMTPTransport.metaClass.sendMessage = { Message msg, Address[] to -> if (aborts-- > 0) throw new Exception("bad"); sent << msg }
    SMTPTransport.metaClass.connect = { -> }
    SMTPTransport.metaClass.getLastServerResponse = { -> return "123-abc" }
    new TemplateBuilderTest().setupTemplates()
  }
  
  @After
  void after() {
    reset()
    new TemplateBuilderTest().after()
  }
  
  void reset() {
    aborts = 0
    sent.clear()
    SMTPTransport.metaClass = null
  }
  
  @Test
  void should_load_config() {
    def smtp = new SmtpService()
    assert smtp.config
    smtp.config.smtp = [:]
    smtp.config.smtp['mail.host'] = 'something'
    assert smtp.config?.size() == 7
    assert smtp.config.smtp["mail.host"] == "something"
    smtp = new SmtpService()
    assert smtp.config.smtpMaxAttempts == 2
  }
  
  @Test
  void should_throw_exception_not_configured() {
    assertNotConfigured(null)
    assertNotConfigured([:])
    assertNotConfigured([smtp:[:]])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:[]])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:['host1'], smtpUsernames:[]])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:['host1'], smtpUsernames:['user1'], smtpPasswords:[]])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:['host1', 'host2'], smtpUsernames:['user1'], smtpPasswords:['password1']])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:['host1'], smtpUsernames:['user1', 'user2'], smtpPasswords:['password1']])
    assertNotConfigured([smtp:["mail.smtp.port":"587"], smtpHosts:['host1'], smtpUsernames:['user1'], smtpPasswords:['password1', 'password2']])
  }
  
  void assertNotConfigured(config) {
    def smtp = new SmtpService()
    smtp.config = config
    try {
      smtp.email('templateId', 'email@sub.domain', [:])
      fail('Expected NotConfigured exception')
    }
    catch(SmtpException e) {
      assert true
    }
  }
  
  @Test
  void should_support_whitelist() {
    assert testWhitelist("1test@domain.com", null)
    assert testWhitelist("2test@domain.com", [])
    assert testWhitelist("domain.com", [])
    assert testWhitelist("3test@domain.com", ["domain.com"])
    assert testWhitelist("4test@domain.com", ["domain.com", "acme.com"])
    assert testWhitelist("5test@domain.com", ["acme.com", "domain.com"])
    assert testWhitelist("6test@domain.com", ["6test@domain.com"])
    assert testWhitelist("7test@domain.com", ["acme@domain.com", "7test@domain.com"])
    assert testWhitelist("8test@domain.com", ["8test@domain.com", "acme@domain.com"])
    assert !testWhitelist("9test@domain.com", ["no.com"])
    assert !testWhitelist("atest@domain.com", ["no.com", "acme.com"])
    assert !testWhitelist("btest@domain.com", ["acme.com", "no.com"])
    assert !testWhitelist("ctest@domain.com", ["test@no.com"])
    assert !testWhitelist("dtest@domain.com", ["acme@domain.com", "test@no.com"])
    assert !testWhitelist("etest@domain.com", ["test@no.com", "acme@domain.com"])
    assert testWhitelist("ftest@domain.com", ["acme.com","ftest@domain.com"])
  }
  
  def testWhitelist(email, whitelist) {
    def emailed
    def validated
    sent.clear()
    def smtp = new SmtpService()
    smtp.config.smtp = [:]
    smtp.config.smtp['mail.smtp.port'] = '587'
    smtp.config.smtpWhitelist = whitelist
    smtp.config.smtpHosts = ['host1']
    smtp.config.smtpUsernames = ['user1']
    smtp.config.smtpPasswords = ['password1']
    smtp.email("template1", email, [:])
    return sent.size() > 0
  }

  @Test
  void should_retry() {
    aborts = 1
    def paused = 0
    def smtp = new SmtpService() { def pause(ms) { paused+=ms }}
    smtp.config.smtp = [:]
    smtp.config.smtp['mail.smtp.port'] = '587'
    smtp.config.smtpMaxAttempts=2
    smtp.config.smtpHosts = ['host1']
    smtp.config.smtpUsernames = ['user1']
    smtp.config.smtpPasswords = ['password1']
    SmtpService.log.track { tracker ->
      smtp.email("template1", "test@mycompany.invalid", [:])
      assert sent.size() == 1
      assert paused == 1000
      assert tracker.isLogged("reason=\"java.lang.Exception: bad\"")
    }
  }

  @Test
  void should_retry_custom_settings() {
    aborts = 2
    def paused = 0
    def smtp = new SmtpService() { def pause(ms) { paused+=ms }}
    smtp.config.smtp = [:]
    smtp.config.smtp['mail.smtp.port'] = '587'
    smtp.config.smtpMaxAttempts=3
    smtp.config.smtpRetryDelayMs=2000
    smtp.config.smtpHosts = ['host1']
    smtp.config.smtpUsernames = ['user1']
    smtp.config.smtpPasswords = ['password1']
    SmtpService.log.track { tracker ->
      smtp.email("template1", "test@mycompany.invalid", [:])
      assert sent.size() == 1
      assert paused == 4000
      assert tracker.isLogged("reason=\"java.lang.Exception: bad\"")
    }
  }

  @Test
  void should_send_multipart_message() {
    def smtp = new SmtpService()
    smtp.config.smtp = [:]
    smtp.config.smtp['mail.smtp.port'] = '587'
    smtp.config.smtpHosts = ['host1']
    smtp.config.smtpUsernames = ['user1']
    smtp.config.smtpPasswords = ['password1']
    SmtpService.log.track { tracker ->
  	  smtp.email("template1", "test@mycompany.invalid", ['user':'jsmith','first':'jack','last':'smith'])
      assert tracker.isLogged("Sent SMTP message; msgId=\"123-abc\"; url=\"host1:587\"; templateId=\"template1\"; recipients=\"test@mycompany.invalid\"")
      assert tracker.isLogged("test@mycompany.invalid")
    }
    assert sent.size() == 1
    assert sent[0].from[0].address == "user@host.domain"
    assert sent[0].from[0].personal == "Big Corp"
    assert sent[0].replyTo[0].address == "reply@here.domain"
    assert !sent[0].replyTo[0].personal
    assert sent[0].getSubject() == "Test"
    assert sent[0].getRecipients(Message.RecipientType.TO)[0].address == "test@mycompany.invalid"
    assert sent[0].content.getBodyPart(0).contentType == "text/html; charset=utf-8"
    assert sent[0].content.getBodyPart(0).content == "<html>hello jsmith</html>"
    assert sent[0].content.getBodyPart(1).contentType == "text/plain; charset=utf-8"
    assert sent[0].content.getBodyPart(1).content == "goodbye\njack\nsmith"
  }
  
  @Test
  void should_wrap_and_throw_email_service_exception_invalid_addresses(){
    SMTPTransport.metaClass.sendMessage = { Message msg, Address[] to -> throw new MessagingException("Test message for SendFailedException for unittest") }
    
    def smtp = new SmtpService()
    smtp.config.smtp = [:]
    smtp.config.smtpHosts = ['host1', 'host2', 'host3']
    smtp.config.smtpUsernames = ['user1', 'user2', 'user3']
    smtp.config.smtpPasswords = ['password1', 'password2', 'password3']
    smtp.config.smtp['mail.smtp.port'] = '587'
    SmtpService.log.track { logger ->
      try{
        smtp.email("template1", "invali..dnotsent@mycompany.invalid", ['user':'jdoe','first':'john','last':'doe'])
        fail()
      }catch(SmtpException e){
        assert e.message.contains("Failed to send email after")
      }
    }
  }
    
  void assertSmtpSession(smtp, failover, expectedHost, expectedUsername, expectedPassword) {
    SMTPTransport.metaClass.sendMessage = { Message msg, Address[] to -> 
      if(failover) {
        throw new Exception('Failed to send smtp message')
      }
    }
    try {
      smtp.email('template1', 'email@sub.domain', [:])
    }
    catch(SmtpException e) {
    }
    
    def session = smtp.createSession(0)
    def auth = session.authenticator.getPasswordAuthentication()
    assert expectedHost == session.props["mail.host"]
    assert expectedUsername == auth.userName
    assert expectedPassword == auth.password
  }
}

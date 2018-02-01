package toxic.job

import org.junit.*

class NotificationsTest {
  @Before
  @After
  void reset() {
    email.SmtpService.singleton = null
    template.TemplateBuilder.singleton = null
  }
  
  @Test
  public void should_create_smtp_and_templatebuilder() {
    def props = [t:1]
    email.SmtpService.instance.config = [:]
    def smtp = Notifications.createSmtp(props)
    assert smtp.config.t == 1
    assert smtp.config.smtp.t == 1
    assert smtp.config.smtpUsername == "unknown"
    assert smtp.config.smtpPassword == "unknown"
    assert smtp.config.smtpMaxAttempts == 2
    assert smtp.config["mail.smtp.port"]                == "587"
    assert smtp.config["mail.smtp.socketFactory.port"]  == smtp.config["mail.smtp.port"]
    assert smtp.config["mail.smtp.auth"]                == "true"
    assert smtp.config["mail.smtp.socketFactory.class"] == "javax.net.ssl.SSLSocketFactory"
    assert smtp.config["mail.smtp.starttls.enable"]     == "true"
    assert smtp.config["mail.smtp.timeout"]             == "5000"
    assert smtp.config["mail.smtp.connectiontimeout"]   == smtp.config["mail.smtp.timeout"]
    
    template.TemplateBuilder.instance.config = [:]
    def tb = Notifications.createTemplateBuilder(props)
    assert tb.config.t == 1
    assert tb.config.template.t == 1
    assert tb.config.template.path.endsWith("/conf/templates")
  }

  @Test
  public void should_set_if_needed() {
    def props = [smtpMaxAttempts:3]
    email.SmtpService.instance.config = [:]
    def smtp = Notifications.createSmtp(props)
    assert smtp.config.smtpMaxAttempts == 3
  }
}
package email

import javax.activation.*
import javax.mail.*
import javax.mail.internet.*

import template.*
import log.Log

@Singleton(strict=false) // This is worthless; only leaving it in for documentation purposes
class SmtpService {
  private final static Log log = Log.getLogger(this)
  private final static DEFAULT_FAILED_RESET_MS = 1800000 // 30 minutes
  
  static singleton
  
  static SmtpService getInstance() {
    if (!singleton) {
      synchronized(this) {
        if (!singleton) {
          singleton = new SmtpService()
        }
      }
    }
    return singleton
  }  

  def config
  
  SmtpService() {
    this.config = [smtp:[:]]
    config.with {
      smtp["mail.smtp.port"]                = "587"
      smtp["mail.smtp.socketFactory.port"]  = smtp["mail.smtp.port"]
      smtp["mail.smtp.auth"]                = "true"
      smtp["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
      smtp["mail.smtp.starttls.enable"]     = "true"
      smtp["mail.smtp.timeout"]             = "3000"
      smtp["mail.smtp.connectiontimeout"]   = smtp["mail.smtp.timeout"]
      smtpHosts                             = ['smtp.mycompany.invalid']
      smtpUsernames                         = ['username1']
      smtpPasswords                         = ['password1']
      smtpMaxAttempts                       = 2
      smtpRetryDelayMs                      = 1000
      smtpWhitelist                         = []
    }
  }

  SmtpService(Map configuration) {
    this.config = configuration
  }
  
  def inWhitelist(email) {
    def result = true
    def inPieces = email?.tokenize("@")
    def whitelist = config?.smtpWhitelist
    if (whitelist && inPieces?.size() == 2) {
      result = false
      for (def entry : whitelist) {
        def pieces = entry?.tokenize("@")
        def testEmail = email
        if (pieces.size() == 1) {
          testEmail = inPieces[1]
        }
        if (entry == testEmail) {
          result = true
          break
        } 
      }
    }
    return result
  }

  /**
   * Send email to recipient using specified template
   * 
   * @param templateId notification template ID
   * @param destAddress email address of recipient 
   * @param attributes map of key/value attributes
   */
  public void email(String templateId, String destAddress, Map attributes) {
    validateConfig()
    
    if (config.smtp.recipient) {
      destAddress = config.smtp.recipient
      if (log.debug) log.debug("using recipient override; recipient=${recipient}")
    } else if (!inWhitelist(destAddress)) {
      log.info("Recipient is not in white list; discarding email; destAddress=${destAddress}")
      return
    }
    
    def smtpIndex = 0 // could support multiple SMTP configs in case one goes down
    def msg = createMessage(smtpIndex, templateId, destAddress, attributes)
    
    int attempt = 0
    def maxAttempts = config.smtpMaxAttempts ?: 1
    def retryDelayMs = config.smtpRetryDelayMs ?: 1000
    def sent = false
    while (!sent && attempt++ < maxAttempts) {
      def transport
      try {
        transport = msg.session.getTransport("smtp")
        transport.connect()
        transport.sendMessage(msg, msg.getAllRecipients())
        def msgId = transport.lastServerResponse ? (transport.lastServerResponse - "250 Ok ").trim() : ""
        log.info("Sent SMTP message; msgId=\"${msgId}\"; url=\"${msg.session.props['mail.host']+':'+msg.session.props['mail.smtp.port']}\"; templateId=\"${templateId}\"; recipients=\"${destAddress}\"")
        sent = true
      } catch (Exception e) {
        log.info("Failed to send email; attempt=${attempt}; maxAttempts=${maxAttempts}; retryDelayMs=${retryDelayMs}; templateId=${templateId}; from=${msg.from ? msg.from[0].address : null}; recipients=${destAddress}; reason=\"${e.message}\"", log.debug ? e : null)
        if (attempt >= maxAttempts) {
          throw new SmtpException("Failed to send email after ${attempt} attempts", e)
        }
        pause(retryDelayMs)
      } finally {
        if (transport) {
          try { transport.close() } catch (MessagingException me) {}
        }
      }
    }
  }
  
  public void validateConfig() {
    if (!config 
      || !config.smtp 
      || !config.smtpHosts 
      || !config.smtpUsernames 
      || !config.smtpPasswords
      || config.smtpHosts.size() != config.smtpUsernames.size()
      || config.smtpHosts.size() != config.smtpPasswords.size()) {
      throw new SmtpException("Invalid SMTP configuration")
    }
  }
  
  def pause(delay) {
    sleep(delay)
  }
  
  /*
   * @throws NotFound if the template does not exist
   */
  def createMessage(smtpIndex, templateId, destAddress, attributes) {
    def template = TemplateBuilder.instance.build(templateId)

    MimeMessage msg = new MimeMessage(createSession(smtpIndex));
    msg.setRecipients(MimeMessage.RecipientType.TO,createRecipients(destAddress));
    msg.setFrom(new InternetAddress(template.fromEmail, template.fromName, "utf-8"));
    if (template.replyToEmail) msg.setReplyTo([new InternetAddress(template.replyToEmail)] as Address[]);
    msg.setSubject(template.subject);
    msg.setContent(createContents(template, attributes))
    msg.saveChanges()

    return msg    
  }
  
  def createSession(smtpIndex) {
    Properties props = new Properties();
    props.putAll(config.smtp)
    props.put("mail.host", config.smtpHosts[smtpIndex])
    Session session = Session.getInstance(props,new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(config.smtpUsernames[smtpIndex], config.smtpPasswords[smtpIndex]);
      }
    });
    return session  
  }
  
  def createRecipients(recipients) {
    return recipients.split("[,;]").collect { new InternetAddress(it) }.toArray(new InternetAddress[0])
  }

  def createContents(template, attributes) {
    Multipart multipart = new MimeMultipart("alternative");
    template.contents.each { entry ->
      MimeBodyPart part = new MimeBodyPart();

      def emailContent = TemplateBuilder.instance.personalize(entry.content, attributes, template)

      part.setContent(emailContent, entry.type);
      multipart.addBodyPart(part)
    }
    return multipart    
  }
}


package toxic.job

import org.apache.log4j.*

class Notifications {
  private static Logger log = Logger.getLogger(this)

  static def setIfNeeded(props, key, value) {
    if (!props[key]) props[key] = value
  }
  
  static def createSmtp(props) {
    email.SmtpService.instance.config = [:]
    def smtp = email.SmtpService.instance
    synchronized(smtp) {
      if (!smtp.config) {
        smtp.config = props
        smtp.config.smtp = smtp.config
        smtp.config.smtpUsername = smtp.config["secure.smtpUsername"] ?: "unknown"
        smtp.config.smtpPassword = smtp.config["secure.smtpPassword"] ?: "unknown"
        smtp.config.smtpHosts = [smtp.config["mail.host"]]
        smtp.config.smtpUsernames = [smtp.config.smtpUsername]
        smtp.config.smtpPasswords = [smtp.config.smtpPassword]
        setIfNeeded(smtp.config,"smtpMaxAttempts",                2)
        setIfNeeded(smtp.config,"mail.smtp.port",                 "587")
        setIfNeeded(smtp.config,"mail.smtp.socketFactory.port",   smtp.config["mail.smtp.port"])
        setIfNeeded(smtp.config,"mail.smtp.auth",                 "true")
        setIfNeeded(smtp.config,"mail.smtp.socketFactory.class",  "javax.net.ssl.SSLSocketFactory")
        setIfNeeded(smtp.config,"mail.smtp.starttls.enable",      "true")
        setIfNeeded(smtp.config,"mail.smtp.timeout",              "5000")
        setIfNeeded(smtp.config,"mail.smtp.connectiontimeout",    smtp.config["mail.smtp.timeout"])
      }
    }
    createTemplateBuilder(props)
    
    return smtp
  }

  static def createTemplateBuilder(props) {
    template.TemplateBuilder.instance.config = [:]
    def templateBuilder = template.TemplateBuilder.instance
    synchronized(templateBuilder) {
      if (!templateBuilder.config) {
        templateBuilder.config = props
        setIfNeeded(templateBuilder.config, "path", "${System.getenv("TOXIC_HOME")}/conf/templates")
        templateBuilder.config.template = templateBuilder.config
      }
    }
    return templateBuilder
  }  
}
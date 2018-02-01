package toxic.job

import org.apache.log4j.*
import javax.mail.*
import javax.mail.internet.*
import javax.activation.*
import groovy.xml.*

public class SmtpNotification implements JobNotification {
  private static Logger log = Logger.getLogger(this)
  protected Job job
  protected Map jobSimple
  protected int maxFailures
  protected int maxAttempts

  public SmtpNotification() {}
    
  public boolean execute(Job job) {
    this.job = job
    this.jobSimple = job.toSimple()
    this.maxFailures = job.properties["job.smtp.tasklimit"] ? job.properties["job.smtp.tasklimit"].toInteger() : 100
    this.maxAttempts = job.properties["job.smtp.attempts"] ? job.properties["job.smtp.attempts"].toInteger() : 3
    
    if (shouldMail()) {
      Properties props = new Properties();
      props.setProperty("mail.host",job.properties["job.smtp.host"]);
      String port = job.properties["job.smtp.port"] ?: null
      if (job.properties["job.smtp.username"]) {
        props.setProperty("mail.smtp.auth", "true");
        port = port ?: "587";
        if ("false".equalsIgnoreCase(job.properties["job.smtp.starttls"].toString())) {
          props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
          props.setProperty("mail.smtp.starttls.enable", "false");
          port = port ?: "465";
        } else {
          props.setProperty("mail.smtp.starttls.enable", "true");
        }
      } else {
        port = port ?: "25"
      }
      props.setProperty("mail.smtp.port", port);
      props.setProperty("mail.smtp.socketFactory.port", port);
      
      Session session = Session.getDefaultInstance(props,new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(job.properties["job.smtp.username"], job.properties["job.smtp.password"]);
        }
      });
      MimeMessage msg = new MimeMessage(session);
      
      def to = job.properties["job.smtp.recipients"].split("[,;]").collect {
        new InternetAddress(it)
      }.toArray(new InternetAddress[0])
      
      msg.setRecipients(MimeMessage.RecipientType.TO,to);
      msg.setFrom(new InternetAddress(job.properties["job.smtp.sender"] ?: "toxic@localhost"));
      msg.setSubject(job.properties["job.smtp.subject"] ?: "${localize('Toxic')} - ${outcome}");

      Multipart mp = new MimeMultipart("alternative");

      MimeBodyPart mbp = new MimeBodyPart();
      mbp.setContent(contentText, "text/plain; charset=utf-8");
      mp.addBodyPart(mbp)
      
      mbp = new MimeBodyPart();
      mbp.setContent(contentHtml, "text/html; charset=utf-8");
      mp.addBodyPart(mbp)
      
      msg.setContent(mp)
 
      log.info("Sending SMTP job notification; jobId=${job.id}; host=${job.properties['job.smtp.host']}; port=${port}; recipients=${job.properties['job.smtp.recipients']}")
      int attempt = 0
      while (attempt++ < maxAttempts) {
        try {
          Transport.send(msg);      
          break
        } catch (Exception e) {
          if (attempt >= maxAttempts) {
            def reason = JobManager.findReason(e)
            log.error("Failed to send notification email; attempts=${attempt}; reason=${reason}")
            job.properties["job.smtp.error"] = reason
            throw e
          }
        }
      }
      log.info("Send SMTP complete; jobId=${job.id}")
    }
  }
  
  def getContentHtml() {
    StringBuffer sb = new StringBuffer()
    sb.append("${outcome}")
    sb.append("<p>")
    sb.append("<table style='width: 85%; margin: 10px; padding: 4px; background-color: #FFFFEE; border: 1px solid #D3D3D3'>\n")
    sb.append("<thead style='text-align: left; color: white; font-weight: bold; background-color: ${jobSimple.failed ? '#BB0000': '#33DD33'}'>\n")
    sb.append("<tr><th>${localize('Suite')}</td><td>${localize('Tasks')}</td><td>${localize('Duration')}</td></tr>\n")
    sb.append("</thead>")
    def suites = job.toSuiteBreakdown(0, Long.MAX_VALUE).findAll { !it.success }
    if (!suites) {
      sb.append("<tr><td colspan='3'>${localize('No failed tests')}</td></tr>\n")
    } else {
      for (int idx = 0; idx < suites.size(); idx++) {
        def suite = suites[idx]
        if (idx < maxFailures) {
          sb.append("<tr><td>${suite.suite}</td><td>${suite.tasks}</td><td>${suite.duration}ms</td></tr>\n")
        } else if (idx == maxFailures) {
          sb.append("<tr><td colspan='3'>...</td></tr>\n")
        } else {
          break
        }
      }
    }
    sb.append("</table>\n")
    return sb.toString()
  }
  
  def getContentText() {
    StringBuffer sb = new StringBuffer()
    sb.append("${outcome}")
    sb.append("\n\n")
    def suites = job.toSuiteBreakdown(0, Long.MAX_VALUE).findAll { !it.success }
    if (!suites) {
      sb.append("${localize('No failed tests')}\n")
    } else {
      for (int idx = 0; idx < suites.size(); idx++) {
        def suite = suites[idx]
        if (idx < maxFailures) {
          sb.append("${suite.suite} (${suite.tasks}): ${suite.duration}ms\n")
        } else if (idx == maxFailures) {
          sb.append("...\n")
        } else {
          break
        }
      }
    }
    return sb.toString()
  }

  String getOutcome() {
    return "${job.id}: ${jobSimple.failed ? localize("FAILED") : localize("SUCCESS")}"
  }
  
  boolean shouldMail() {
    if (!job?.properties) return false
    
    def mailOnSuccess = !"false".equalsIgnoreCase(job.properties["job.smtp.onsuccess"].toString())
    def mailOnFailure = !"false".equalsIgnoreCase(job.properties["job.smtp.onfailure"].toString())
    
    return job.properties["job.smtp.host"] && 
           job.properties["job.smtp.recipients"] &&
           ((mailOnSuccess && !this.jobSimple?.failed) ||
            (mailOnFailure && this.jobSimple?.failed))
  }
  
  String localize(String key) {
    return key
  }
}
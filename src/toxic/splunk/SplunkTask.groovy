package toxic.splunk

import javax.servlet.http.*
import toxic.TaskResult
import toxic.xml.XmlTask
import org.apache.log4j.Logger

public class SplunkTask extends XmlTask {
  protected static Logger slog = Logger.getLogger(SplunkTask.class.name)
  
  public void init(def input, def props) {
    super.init(input, props)
    if (input instanceof File) {
      def fileProps = new Properties()
      fileProps.load(input.newInputStream())
      fileProps.findAll { k, v -> k.startsWith("splunk_") }.each { k, v ->
        props.put(k, replace(v))
        fileProps.remove(k)
      }
      reqContent = ""
      if (fileProps.data) {
        reqContent = fileProps.data
      } else {
        fileProps.each { k, v -> 
          if (reqContent) reqContent += "&"
          reqContent += "${k}=${URLEncoder.encode(v)}"
        }
      }
    } else {
      throw new IllegalArgumentException("input is not a File")
    }
  }
  
  public List<TaskResult> doTask(def memory) {
    def oldProps = memory.findAll { k, v -> k.startsWith("xml") }
    if (props.splunk_hostname) props.xmlHost = props.splunk_hostname
    if (props.splunk_port) props.xmlPort = props.splunk_port
    if (props.splunk_ssl) props.xmlSsl = props.splunk_ssl
    def ret = super.doTask(memory)
    memory.putAll(oldProps)
    return ret
  }

  protected String headers() {
    def result = super.headers()
    result += headers("splunk.header.")
    return result
  }
  
  def stripHeaders(content) {
    def idx = content?.indexOf("\r\n\r\n")
    if (idx > 0) content = content.substring(idx).trim()
    return content
  }
  
  def parseSid(resp) {
    def sid
    if (resp) {
      def xml = new XmlParser().parseText(stripHeaders(resp))
      sid = xml.sid?.text()
      log.info("Created Splunk job; sid=${sid}")
    }
    return sid
  }
  
  def parseStatus(resp) {
    def state
    if (resp) {
      def xml = new XmlParser().parseText(stripHeaders(resp))
      state = xml.content?."s:dict"?."s:key"?.find { it.attribute("name") == "dispatchState" }?.text()
    }
    return state
  }
  
  def pause(long time) {
    sleep(time)
  }
  
  def waitForSid(uri, memory) {
    def finished = false
    if (uri) {
      def timeout = memory.splunk_timeout ? Integer.parseInt(memory.splunk_timeout) : 60000
      def startTime = System.currentTimeMillis()
      while(!finished && ((System.currentTimeMillis() - startTime) < timeout)) {
        pause(500)
        def state = parseStatus(sendToSplunk(uri, "", memory))
        if (state == "DONE")  {
          log.info("Splunk job complete; state=${state}")
          finished = true
        } else {
          log.info("Splunk job in progress; state=${state}")  
        }
      }
    }
    return finished
  }
  
  def sendToSplunk(uri, content, memory) {
    memory.xmlMethod = uri + " HTTP/1.1"
    return super.transmit(prepare(content), memory)
  }  

  protected String transmit(request, memory) {
    def resp = sendToSplunk(memory.splunk_method, reqContent, memory)
    def sid = parseSid(resp)
    if (sid && waitForSid("GET /services/search/jobs/${sid}", memory)) {
      resp = sendToSplunk("GET /services/search/jobs/${sid}/results", "", memory)
    }
    return resp
  }

}

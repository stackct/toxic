package toxic.slack

import org.apache.log4j.*
import javax.websocket.*
import org.glassfish.tyrus.client.*
import groovy.json.*
import toxic.*
import toxic.job.*
import toxic.user.*

@Singleton(strict=false)
class SlackBot extends Endpoint implements Runnable, UserSource {
  private static Logger log = Logger.getLogger(this)

  boolean running
  SlackHandler handler
  def wss
  def slackToken
  int msgId = 0
  def endpoint
  def rtm
  long silenceEndTime
  
  private SlackBot() {
    endpoint = this
  }
  
  public boolean isSilenced() {
    return System.currentTimeMillis() < silenceEndTime
  }
  
  public void silenceUntil(long time) {
    silenceEndTime = time
  }
  
  public void run() {
    running = true
    while (running) {
      if (shouldReconnect()) connect()
      Thread.sleep(10000)
    }
  }
  
  public void shutdown() {
    running = false
  }
  
  public boolean shouldReconnect() {
    return handler?.config("secure.slack.token") && (!wss?.isOpen() || handler?.config("secure.slack.token") != slackToken)
  }
  
  public void connect() {
    try { wss?.close() } catch (Exception e) {}
    wss = null
    slackToken = handler?.config("secure.slack.token")
    if (!slackToken) {
      log.warn("No secure.slack.token found; will not connect to Slack")
      return
    }
    def resp = new URL("https://slack.com/api/rtm.start?token=${slackToken}").text
    rtm = new JsonSlurper().parseText(resp)
    def wssUrl = rtm?.url
    if (!wssUrl) {
      log.error("Failed to discover Slack websocket URL; slackToken=${slackToken[0..4]}..${slackToken[-4..-1]}; resp=${resp}")
      return
    }
    
    def cec = ClientEndpointConfig.Builder.create().build()
    log.info("Connecting to Slack; wssUrl=${wssUrl}")
    wss = ClientManager.createClient().connectToServer(endpoint, cec, new URI(wssUrl))
  }
  
  @Override
  public void onOpen(Session session, EndpointConfig config) {
    try {
      session.addMessageHandler(new MessageHandler.Whole<String>() {
        @Override
        public void onMessage(String message) {
          def msgJson = new JsonSlurper().parseText(message)
          def methodName = "processMessage_${msgJson?.type}"
          if (endpoint.metaClass.respondsTo(endpoint, methodName)) {
            "${methodName}"(msgJson)
          } else {
            log.debug("Unsupported message; msg=${message}; methodName=${methodName}")
          }
        }
        })
      log.info("Connected to Slack")

    } catch (Exception e) {
      log.error("Failed to open session; session=${session}; config=${config}; reason=${JobManager.findReason(e)}")
    }
  }
  
  @Override
  public void onError(Session session, Throwable t) {
    log.error("Unexpected error; reason=${JobManager.findReason(t)}", t)
  }

  public User getById(String id) {
    findUser(id, null, null)
  }

  public User find(String id, Map options=[:]) {
    def slackUser = findUser(id, options?.name, options?.email)

    if (slackUser) {
      return new User(id:slackUser.id, name: slackUser.name, profile: slackUser.profile)
    }
  }
  
  def findChannel(name) {
    def target = rtm ? (rtm.channels + rtm.groups).find { (name in [it.name, it.id]) && (it.is_member || it.is_group) } : null
    if (!target) {
      log.info("Channel not eligible; name=${name}")
    }
    return target
  }

  def findUser(user, name = null, email = null) {
    def target = rtm?.users?.find { 
      user?.equalsIgnoreCase(it.id) || user?.equalsIgnoreCase(it.name) || email?.equalsIgnoreCase(it.profile?.email) || name?.equalsIgnoreCase(it?.real_name)
    }
    if (!target) {
      log.debug("User not eligible; user=${user}; name=${name}; email=${email}")
    }
    return target
  }
  
  def processMessage_message(def msg) {
    def resp = handler.handleCommand(this, msg)
    if (resp) {
      sendMessage(msg.channel, resp)
    }
  }
  
  def sendMessageToChannels(String names, String message) {
    int sent = 0
    if (shouldReconnect()) return false
    names.split(",").each { name ->
      def channel = findChannel(name)
      if (channel) {
        sendMessage(channel.id, message)
        sent++
      }
    }
    return sent > 0
  }

  def sendMessageToUsers(String names, String message) {
    int sent = 0
    if (shouldReconnect()) return false
    names.split(",").each { name ->
      def channelId = createIMChannel(name)
      if (channelId) {
        sent++
        return sendMessage(channelId, message)
      }
    }
    return sent > 0
  }  

  def send(msg) {
    msg.id = msgId++
    wss.asyncRemote?.sendText(JsonOutput.toJson(msg))
  }
  
  def sendMessage(channelId, message) {
    send([type:"message",channel:channelId,text:message])
  }
  
  def post(uri,args) {
    def result
    def conn = new URL("https://slack.com/api/${uri}").openConnection()
    conn.setRequestMethod("POST")
    conn.doOutput = true
    def delim = ""
    def out = ""
    args.each { k, v -> 
      out += "${delim}${k}=${URLEncoder.encode(v)}"
      delim = "&"
    }
    conn.outputStream << out
    result = conn.inputStream.text
    return new JsonSlurper().parseText(result)
  }
  
  def createIMChannel(username) {
    def user = findUser(username)
    if (user) {
      def results = post("im.open", [user:user.id,token:slackToken])
      return results?.channel?.id
    }
    return false
  }
}

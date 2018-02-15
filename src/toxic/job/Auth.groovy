package toxic.job

import org.apache.log4j.*
import javax.servlet.http.Cookie
import javax.crypto.*
import javax.crypto.spec.*
import java.security.spec.*
import toxic.slack.*
import toxic.user.*

class Auth {
  private static Logger log = Logger.getLogger(this)
  static SecretKey aesKey

  static def setCookie(resp, key, value, age) {
    def cookie = new Cookie(key, value)
    cookie.setMaxAge(age)
    resp.addCookie(cookie)
  }
  
  static def getCookie(req, key) {
    req.cookies.find { cookie -> cookie.name == key }?.value
  }
  
  static def request(props, req, resp, auth, type, loc) {
    auth = sendToken(props, auth, generateToken(props, "auth", auth), type, loc)
    if (auth) {
      return [auth: auth]
    }
    return false;
  }
  
  static def validate(props, req, resp, token) {
    def auth = validateToken(props, "auth", token, getAuthExp(props))

    if (auth) {
      def authtoken = generateToken(props, "session", auth)
      def expMillis = getSessionExp(props)
      setCookie(resp, "authtoken", authtoken, (int)(expMillis / 1000))
      log.info("Authenticated token; auth=${auth}")

      return [auth: auth, token: authtoken, expiration: new Date().time + expMillis]
    }
    return false;
  }
  
  static def identify(props, req, resp, token) {
    def data = [authenticated:false, auth: (req?.getHeader('X-Forwarded-For') ?: req?.remoteHost)]
    def auth = req.getHeader("X-Auth-Request-User") ?: req.getHeader("X-User")
    if (auth) {
      log.debug("Identified user from X header; user=${auth}")
    } else {
      token = token ?: getCookie(req, "authtoken")
      auth = validateToken(props, "session", token, getSessionExp(props))
    }

    if (auth) {
      log.debug("Locating Slack user; auth=${auth}")

      def slackUser = UserManager.instance.find(auth, SlackBot.instance)

      if (slackUser) {
        slackUser.with { u ->
          log.debug("Slack user found; auth=${auth}; user=${u}")

          data += u.toMap()
          data['authenticated'] = true

          if (!UserManager.instance.getById(u?.id)) {
            log.debug("Adding user to ConfigManager; user=${u.id}")
            UserManager.instance.add(u)
          }
        }
      }
    }
    return data
  }
  
  static def getAuthExp(props) {
    props["authTokenExpirationMs"] ? new Long(props["authTokenExpirationMs"]) : 10 * 60 * 1000
  }
  
  static def getSessionExp(props) {
    props["sessionTokenExpirationMs"] ? new Long(props["sessionTokenExpirationMs"]) : 7 * 24 * 60 * 60 * 1000
  }
  
  static def getCipher(props, boolean encrypt) {
    if (!aesKey) {
      KeyGenerator kgen = KeyGenerator.getInstance("AES");
      kgen.init(128);
      aesKey = kgen.generateKey();
    }
    IvParameterSpec iv = new IvParameterSpec("AB31BFCC94E32F11".getBytes("UTF-8"));
    def cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    if (encrypt) {
      cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv)
    } else {
      cipher.init(Cipher.DECRYPT_MODE, aesKey, iv)
    }
    return cipher
  }

  static def generateToken(props, type, auth) {
    def unencryptedToken = "${type}|${new Date().time}|${auth}"
    def encrypted = getCipher(props, true).doFinal(unencryptedToken.getBytes('UTF-8'))
    def token = encrypted.encodeBase64().toString()
    log.info("Generated token; type=${type}; auth=${auth}")
    return token
  }
  
  static def validateToken(props, type, token, exp) {
    def payload = false
    if (token) {
      def decoded = token.decodeBase64()
      def unencryptedToken = new String(getCipher(props, false).doFinal(decoded), 'UTF-8')
      def pieces = unencryptedToken.tokenize("|")
      if (pieces?.size() == 3) {
        def actualType = pieces[0]
        def time = pieces[1]
        def data = pieces[2]
        
        if ((actualType == type) && (time.toLong() > (new Date().time - new Long(exp)))) {
          log.debug("Validated token; type=${type}; data=${data}")
          payload = data
        } else {
          log.info("Validated expired token; type=${type}; data=${data}")
        }
      }
    }
    return payload
  }
  
  static def sendSlack(props, templateId, auth, attributes) {
    def templateBuilder = Notifications.createTemplateBuilder(props)
    def template = templateBuilder.build(templateId)
    def message = templateBuilder.personalize(template.contents.first().content, attributes)

    def slack = SlackBot.instance
    def channelId = slack.createIMChannel(auth)
    if (channelId) {
      return slack.sendMessage(channelId, message)
    }
    return false
  }
  
  static def sendToken(props, auth, token, type, loc) {
    def attributes = [:]
    int idx = loc.indexOf("?")
    if (idx > 0) loc = loc[0..idx-1]
    attributes.url = loc + "?token=${URLEncoder.encode(token)}"
    def failure = false
    switch (type?.toLowerCase()) {
      case "email": Notifications.createSmtp(props).email("auth-request", auth, attributes); break;
      case "slack": failure = !sendSlack(props, "auth-request", auth, attributes); break
      default: failure = true
    }
    return !failure
  }  

}
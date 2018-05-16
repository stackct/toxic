package toxic.http

import org.apache.log4j.Logger
import toxic.CompareTask

import javax.net.ssl.SSLSocketFactory
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class HttpTask extends CompareTask {
  protected static Logger slog = Logger.getLogger(HttpTask.class.name)
  static final String HTTP_CR = "\r\n"

  def gzip = false

  protected String headers(def prefix) {
    def result = ""
    props.keySet().sort().each { key ->
      if (key.startsWith(prefix)) {
        def value = props[key]
        if (value) {

          if (key.endsWith("Content-Encoding") && value == "gzip") {
            gzip = true
          }

          result += key.substring(prefix.size()) + ": " + value + HTTP_CR
        }
      }
    }
    return result
  }

  protected String headers() {
    return headers("http.header.")
  }

  /*
   * Returns a string or a byte array depending on whether the body is gzipped
   */
  @Override
  def prepare(str) {
    def result
    if (str.startsWith("GET") || str.startsWith("POST") || str.startsWith("OPTIONS")
        || str.startsWith("HEAD") || str.startsWith("DELETE") || str.startsWith("PUT")
        || str.startsWith("TRACE") || str.startsWith("CONNECT") || !props.httpMethod) {
      result = replace(str)
    }
    else {
      result = props.httpMethod + HTTP_CR
      result += headers()

      def content = replace(str)

      /*
       * If we have to gzip the body then switch the entire thing into a byte stream
       */
      if (gzip) {
        def output = new ByteArrayOutputStream()
        output << result.bytes
        def compressedContent = compress(content)
        output << "Content-Length: ${compressedContent.size()}" + HTTP_CR + HTTP_CR
        output << compressedContent
        result = output.toByteArray()
      }
      else {
        result += "Content-Length: ${content.bytes.size()}" + HTTP_CR + HTTP_CR
        result += content
      }
    }

    return result
  }

  @Override
  protected String transmit(request, expectedResponse, memory) {
    def result

    def ssl = false
    if (memory.httpSsl) {
      ssl = memory.httpSsl == "true"
    }

    int httpRetries = 0
    try {
      httpRetries = new Integer(memory.httpRetries)
    } catch (Exception e) {
      log.debug("httpRetries properties is not set, defaulting to 0 retries; error=${e.message}")
    }

    if (memory.httpVerbose == "true") {
      def content
      if (gzip) content = " Gzip compressed: ${request.size()}"
      else content = "\n${request}"

      log.info("Sending to host=" + memory.httpHost + ":" + memory.httpPort + "; SSL=" + (ssl?"true":"false") + "; retries=${httpRetries}:${content}")
    }

    int attempts = 0
    while (attempts++ <= httpRetries) {
      try {
        def socket
        if (ssl) {
          socket = SSLSocketFactory.getDefault().createSocket(memory.httpHost, new Integer(memory.httpPort.toString()))
          socket.setUseClientMode(true)
        }
        else {
          socket = new Socket(memory.httpHost, new Integer(memory.httpPort.toString()))
        }

        if (memory.httpTimeout) {
          socket.soTimeout = new Integer(memory.httpTimeout.toString())
        }

        socket.withStreams { input, output ->
          if (request instanceof String && (memory.httpLinePause) && (memory.httpLinePause != "0")) {
            request.eachLine {
              log.debug("Pausing before sending next line; pauseDuration=" + memory.httpLinePause + "; nextLine=" + it)
              Thread.sleep(new Long(memory.httpLinePause))
              output << it
            }
          }
          else {
            output << request
          }
          output.flush()

          result = readHttpBody(input)
        }
        break
      }
      catch (javax.net.ssl.SSLHandshakeException e) {
        log.error("Try adding '-Djavax.net.ssl.trustStore=\${TOXIC_HOME}/conf/toxic.jks' as a vm arg.", e)
        throw e
      }
      catch (SocketException se) {
        if (attempts > httpRetries) {
          log.error("Exceeded allowed connection attempts; attempts=${attempts}", se)
          throw se
        } else {
          log.warn("Socket exception; attempts=${attempts}", se)
        }
      }
    }

    if (memory.httpVerbose == "true") {
      log.info("Received:\n" + result)
    }

    return result
  }

  void setHttpConnection() {
    if (props.httpUri) {
      try {
        def url = new URL(props.httpUri)
        def ssl = url.protocol.startsWith('https')

        props['httpHost'] = url.host
        props['httpPort'] = url.port >= 0 ? url.port : (ssl ? 443 : 80)
        props['httpSsl'] = ssl.toString()
      } catch (java.net.MalformedURLException e) {
        log.warn("Could not parse uri: ${props.httpUri}")
      }
    }
  }

  def readHttpBody(input) {
    def response = new StringWriter()
    def bodyLength = 0
    def line = "Keep going"
    while (line.trim()) {
      line = readLine(input)
      if (line != null) {
        // According to the RFC7230 spec, HTTP headers are case-insensitive
        // https://tools.ietf.org/html/rfc7230#section-3.2
        if (line.toLowerCase().startsWith("content-length")) bodyLength = line.split(":")[1].trim().toInteger()
        response << line
      }
    }

    def bodyBytes = new byte[bodyLength]
    def bodyReadLength = 0
    def read = 0
    while ((read != -1) && (bodyReadLength < bodyLength)) {
      read = input.read(bodyBytes, bodyReadLength, bodyLength - bodyReadLength)
      bodyReadLength += read
    }
    if (gzip) {
      response = response.toString() + uncompress(bodyBytes)
    }
    else {
      response = response.toString() + new String(bodyBytes)
    }

    response
  }

  static readLine(input) {
    def writer = new StringWriter()
    def byteRead
    while (-1 != byteRead && (byteRead != 13 && byteRead != 10)) {
      byteRead = input.read()
      if (byteRead != -1) {
        def character = new String(byteRead as byte)
        writer << character

        // Handle CRLF
        if (byteRead == 13) {
          input.mark(0)
          def nextByte = input.read()
          if (nextByte != 10) {
            input.reset()
          }
          else {
            character = new String(nextByte as byte)
            writer << character
          }
        }
      }
    }

    writer.toString()
  }

  static BUFFER_SIZE = 1024 * 1024 * 50
  static byte[] compress(String input) {
    ByteArrayOutputStream out
    GZIPOutputStream gzip

    try {
      out = new ByteArrayOutputStream()
      gzip = new GZIPOutputStream(out)
      def msg = input.bytes
      gzip.write(msg, 0, msg.length)
      gzip.finish()
    }
    finally {
      out?.close()
      gzip?.close()
    }
    return out.toByteArray()
  }

  static String uncompress(byte[] bytes) {
    String out = ""
    GZIPInputStream gzip
    try {
      gzip = new GZIPInputStream(new ByteArrayInputStream(bytes), BUFFER_SIZE)

      byte[] uncompressed = new byte[BUFFER_SIZE]
      def bytesRead = gzip.read(uncompressed, 0, BUFFER_SIZE)

      if (bytesRead > 0) {
        out = new String(uncompressed,0,bytesRead)
      }
    }
    finally {
      gzip?.close()
    }
    return out
  }
}

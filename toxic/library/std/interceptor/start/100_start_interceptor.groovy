import com.sun.net.httpserver.HttpServer
import util.Wait

memory.httpServer = HttpServer.create(new InetSocketAddress(memory.port), 0)
def requests = []
byte[] requestBytes = []

memory.httpServer.with {
    log.info("HttpServer is starting")
    createContext(memory.route) { http ->
        // TODO TWRR expose the requests that are intercepted so that they can be asserted against
        requests.add([headers: http.getRequestHeaders(), body: http.getRequestBody().text, method: http.getRequestMethod()])
        http.responseHeaders.add("Content-type", "text/plain")
        http.sendResponseHeaders(200, 0)
        http.responseBody.withWriter { out ->
            out << "Hello ${http.remoteAddress.hostName}!\n"
        }
    }
    start()
}

import com.sun.net.httpserver.HttpServer
import util.Wait

memory.httpServer = HttpServer.create(new InetSocketAddress(memory.port), 0)
memory.requestCounter = 0
memory.httpServer.with {
    log.info("HttpServer is starting")
    createContext(memory.route) { http ->
        memory.requestCounter++
        http.responseHeaders.add("Content-type", "text/plain")
        http.sendResponseHeaders(200, 0)
        http.responseBody.withWriter { out ->
            out << "Hello ${http.remoteAddress.hostName}!\n"
        }
    }
    start()
}

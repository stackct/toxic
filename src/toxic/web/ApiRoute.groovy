package toxic.web

import spark.*
import log.*
import org.apache.log4j.*

class ApiRoute implements Route {
  private static Logger accessLog = Logger.getLogger("ToxicWebAccess")
  private static Log log = Log.getLogger(this)

  private String route
  private Closure cls
  private boolean useGzip

  public ApiRoute(String route, Closure c, boolean useGzip = true) {
    this.route = route
    this.cls = c
    this.useGzip = useGzip
  }

  public Object handle(Request req, Response resp) {
    long startTime = System.currentTimeMillis()

    if (useGzip) {
      resp.header("Content-Encoding", "gzip")
    }

    def result
    
    try {
      result = cls(req,resp)
    } catch (Exception e) {
      log.error("An error occurred while handling request; route=${route}; e=${e.message}", e)
    }
    
    long elapsed = System.currentTimeMillis() - startTime

    accessLog.info("Web query completed; result=${resp.raw()?.status}; elapsedMs=${elapsed}; origin=${req.ip()}; url=${req.url()}; agent=${req.userAgent()}")

    return result
  }
}

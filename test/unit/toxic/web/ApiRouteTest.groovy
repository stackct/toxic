package toxic.web

import javax.servlet.http.*
import spark.*
import org.junit.*

class ApiRouteTest {

  @Test
  public void should_handle_simple_request() {
    def responseHeaders = [:]

    def request = [ip: { -> "1.1.1.1" }, url: { -> "foo" }, userAgent: { -> "bar" }, headers: { String key -> "deflate, gzip" } ] as Request
    def servletResponse = [getStatus: { -> 0 } ] as HttpServletResponse
    def response = [raw: { -> servletResponse }, header: { k,v -> responseHeaders.put(k,v) }] as Response

    def route = new ApiRoute("/foo", { req, resp -> "bar" }, false) 

    assert route.handle(request, response) == 'bar'
    assert !responseHeaders.find { k,v ->  k == 'Content-Encoding' }
  }

  @Test
  public void should_handle_gzip_request() {
    def responseHeaders = [:]

    def request = [ip: { -> "1.1.1.1" }, url: { -> "foo" }, userAgent: { -> "bar" }, headers: { String key -> "deflate, gzip" } ] as Request
    def servletResponse = [getStatus: { -> 0 } ] as HttpServletResponse
    def response = [raw: { -> servletResponse }, header: { k,v -> responseHeaders.put(k,v) }] as Response

    def route = new ApiRoute("/foo", { req, resp -> "bar" }, true) 

    assert route.handle(request, response) == 'bar'
    assert responseHeaders.find { k,v ->  k == 'Content-Encoding' }
  }

  @Test
  public void should_handle_deflate_request() {
    def responseHeaders = [:]

    def request = [ip: { -> "1.1.1.1" }, url: { -> "foo" }, userAgent: { -> "bar" }, headers: { String key -> "deflate" } ] as Request
    def servletResponse = [getStatus: { -> 0 } ] as HttpServletResponse
    def response = [raw: { -> servletResponse }, header: { k,v -> responseHeaders.put(k,v) }] as Response

    def route = new ApiRoute("/foo", { req, resp -> "bar" }, true) 

    assert route.handle(request, response) == 'bar'
    assert !responseHeaders.find { k,v ->  k == 'Content-Encoding' }
  }

  @Test
  public void should_handle_uncompressed_request() {
    def responseHeaders = [:]

    def request = [ip: { -> "1.1.1.1" }, url: { -> "foo" }, userAgent: { -> "bar" }, headers: { String key -> null } ] as Request
    def servletResponse = [getStatus: { -> 0 } ] as HttpServletResponse
    def response = [raw: { -> servletResponse }, header: { k,v -> responseHeaders.put(k,v) }] as Response

    def route = new ApiRoute("/foo", { req, resp -> "bar" }, true) 

    assert route.handle(request, response) == 'bar'
    assert !responseHeaders.find { k,v ->  k == 'Content-Encoding' }
  }
}

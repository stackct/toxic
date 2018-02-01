package toxic.web

import org.junit.Test

class ContentTypeTest {
  
  @Test
  public void should_determine_mime_type() {
    assert ContentType.getMimeType("index.htm")       == 'text/html'
    assert ContentType.getMimeType("index.html")      == 'text/html'
    assert ContentType.getMimeType("index.partial")   == 'text/html'
    assert ContentType.getMimeType("foo.css")         == 'text/css'
    assert ContentType.getMimeType("foo.json")        == 'application/json'
    assert ContentType.getMimeType("bar.js")          == 'text/javascript'
    assert ContentType.getMimeType("foo.md")          == 'text/markdown'
    assert ContentType.getMimeType("foo.bar.jpg")     == 'image/jpeg'
    assert ContentType.getMimeType("foo.eot")         == 'application/vnd.ms-fontobject'
    assert ContentType.getMimeType("foo.ttf")         == 'application/octet-stream'
    assert ContentType.getMimeType("foo.bar.jpg")     == 'image/jpeg'
    assert ContentType.getMimeType("foo.bar")         == 'application/octet-stream'
    assert ContentType.getMimeType("foo.tag")         == 'text/plain'
    assert ContentType.getMimeType("foo.properties")  == 'text/plain'
    assert ContentType.getMimeType("foo.log")         == 'text/plain'
    assert ContentType.getMimeType("foo.txt")         == 'text/plain'

    assert ContentType.getMimeType("UNKNOWN")         == 'text/html'
  }
  
}

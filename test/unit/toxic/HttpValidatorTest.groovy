// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.junit.*

public class HttpValidatorTest {

  @Test
  public void testSkipEntireAttribute() {
    checkSkip("%>4%")
    checkSkip("%>10%")

    checkSkip("%>4%\n")
  }

  private checkSkip(skipCode) {
    def xv = new HttpValidator()
    def tp = new ToxicProperties()
    xv.init(tp)

    def expected = """HTTP/1.1 200 OK
Date: %%
Content-Language: en-US
Content-Length: %%
Content-Type: text/xml
Connection: close

<xml/>

"""

    def actual = """HTTP/1.1 200 OK
Date: Mon, 23 May 2011 03:34:46 GMT+00:00
Content-Language: en-US
Content-Length: 705
Content-Type: text/xml
Connection: close

<xml/>
"""

    xv.validate(actual, expected, tp)
  }

  @Test
  public void testValidatorTrimsWhitespaceFromEachLine() {
    def xv = new HttpValidator()
    def tp = new ToxicProperties()
    xv.init(tp)

    def expected = """HTTP/1.1 200 OK
Date: %%
Content-Language: en-US
Content-Length: %%
Content-Type: text/xml
Connection: close

<xml/>

"""

    def actual = """HTTP/1.1 200 OK
Date: Mon, 23 May 2011 03:34:46 GMT+00:00
Content-Language: en-US
Content-Length: 705
Content-Type: text/xml
Connection: close

<xml/>
"""

    xv.validate(actual, expected, tp)
  }

  @Test
  public void testValidatorStripHeaders() {
    def xv = new HttpValidator()
    def tp = new ToxicProperties()

    def expected = "<html><body>test</body></html>"
    def actual = """HTTP/1.1 200 OK
Content-Type: text/html
Content-Length: 30

<html><body>test</body></html>"""
    xv.validate(actual, expected, tp)
  }

}

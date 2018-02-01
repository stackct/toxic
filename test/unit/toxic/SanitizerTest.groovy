// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.junit.Test

public class SanitizerTest {
  @Test
  public void test_sanitize() {
    assert "test= password=***; sd=ffs; littlePass=***; pass=***; fooPassword=***" == Sanitizer.sanitize("test= password=oh;noes,; sd=ffs; littlePass=hideme; pass=barf; fooPassword=dsf")
    assert "password=***" == Sanitizer.sanitize("password=oh;noes")
    assert "password=***;" == Sanitizer.sanitize("password=oh;noes;")
  }
}
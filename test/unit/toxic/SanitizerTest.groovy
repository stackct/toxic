// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.junit.Test

public class SanitizerTest {
  
  @Test
  public void should_sanitize_base_terms() {
    assert "test= password=***; sd=ffs; littlePass=***; pass=***; fooPassword=***" == Sanitizer.sanitize("test= password=oh;noes,; sd=ffs; littlePass=hideme; pass=barf; fooPassword=dsf")
    assert "password=***" == Sanitizer.sanitize("password=oh;noes")
    assert "password=***;" == Sanitizer.sanitize("password=oh;noes;")
  }

  @Test
  public void should_sanitize_extra_terms() {
    assert "ok=1; secret=***; SECRET=***; SeCrEt=***" == Sanitizer.sanitize("ok=1; secret=hideme; SECRET=hideme; SeCrEt=hideme", ['secret'])
  }
}
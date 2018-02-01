// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

public class Sanitizer {
  static def sanitize(String content) {
    return content.replaceAll(/(pass|password|Pass|Password)=.+?(;\s|;$|$)/, "\$1=***\$2")
  }
}

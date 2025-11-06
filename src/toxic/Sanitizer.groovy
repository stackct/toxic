
package toxic

public class Sanitizer {
  private static terms = ['pass', 'password']

  static def sanitize(String content, List extras = []) {
    return content.replaceAll("(?i)(${(terms + extras).join('|')})=.+?(;\\s|;\$|\$)", "\$1=***\$2")
  }
}

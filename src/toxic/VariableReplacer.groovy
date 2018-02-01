
package toxic

public class VariableReplacer implements Replacer {
  def props
  def startDelimiter = "%"
  def stopDelimiter = "%"

  public void init(def props) {
    this.props = props
    startDelimiter = props?.varStartDelimiter ? props?.varStartDelimiter : startDelimiter
    stopDelimiter = props?.varStopDelimiter ? props?.varStopDelimiter : stopDelimiter
  }

  public static String pad(def obj, def padCount = "0", def padChar = " ") {
    pad((obj != null ? obj : "").toString(), padCount, padChar)
  }

  public static String pad(String str, def padCount = "0", def padChar = " ") {
    def result
    def cnt = new Integer(padCount)
    if (cnt < 0) {
      cnt *= -1
      if (cnt < str.size()) {
        result = str.substring(str.size() - cnt)
      } else {
        result = str.padLeft(cnt, padChar)
      }
    } else {
      if (cnt < str.size()) {
        result = str.substring(0, cnt)
      } else {
        result = str.padRight(cnt, padChar)
      }
    }
    return result
  }

  def repClosure = {
    def result = it[0]
    if (it[0]?.size() > 2) {
      def padArgs = it[0][1..-2].tokenize(",")
      def key = padArgs[0]
      def tmp = props?.get(key)
      if (tmp != null) {
        result = tmp
        if (padArgs.size() > 2) {
          result = pad(result.toString(), padArgs[1], padArgs[2])
        }
        else if (padArgs.size() > 1) {
          result = pad(result.toString(), padArgs[1])
        }
      } 
    }
    return result
  }

  public def replace(def input) {
    return input.replaceAll("(\\${startDelimiter}[\\+,!]?[a-zA-Z0-9_]+,?-?[0-9]*,?[^\\${stopDelimiter}\\n]?\\${stopDelimiter})", repClosure)
  }
}
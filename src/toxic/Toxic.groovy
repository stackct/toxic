
package toxic

import util.DateTime

public class Toxic {

  static def props

  static {
    reset()
  }

  static void reset() {
    props = [:]
    loadVersion()
    loadBuildDate()
  }

  static void loadVersion() {
    String versionString = loadResourceFile('VERSION')
    if(versionString) {
      props.version = versionString
    }
  }

  static void loadBuildDate() {
    String buildDateTimeString = loadResourceFile('BUILDDATE')
    if(buildDateTimeString) {
      Date buildDateTime = DateTime.parseZulu(buildDateTimeString, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      props.buildDate = DateTime.format(buildDateTime, 'yyyy-MM-dd')
      props.buildTime = DateTime.format(buildDateTime, 'HH:mm:ss')
    }
  }

  static loadResourceFile(String name) {
    ClassLoader.getSystemResourceAsStream(name)?.text
  }

  public static String getVersion() {
    return props.version
  }

  public static String getBuildDate() {
    return props.buildDate
  }

  public static String getBuildTime() {
    return props.buildTime
  }

  /**
   * Returns a formatted string containing the product and current lib version.
   */
  public static String genProductVersionString(String title) {
    return title + " - Version " + version + " (" + buildDate + " " + buildTime + ")"
  }
}

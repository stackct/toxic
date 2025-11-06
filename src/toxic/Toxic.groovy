
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
  }

  static void loadVersion() {
    String versionString = loadResourceFile('VERSION')
    if(versionString) {
      props.version = versionString
    }
  }

  static loadResourceFile(String name) {
    ClassLoader.getSystemResourceAsStream(name)?.text
  }

  public static String getVersion() {
    return props.version
  }

  /**
   * Returns a formatted string containing the product and current lib version.
   */
  public static String genProductVersionString(String title) {
    return title + " - Version " + version
  }
}

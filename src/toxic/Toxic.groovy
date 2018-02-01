
package toxic

public class Toxic {

  static Properties props = new Properties()

  static {
    def propsFilename = "/" + Toxic.class.simpleName.toLowerCase() + "-version.properties"
    def versionStream = Toxic.class.getResourceAsStream(propsFilename)
    
    if (versionStream) {
      props.load(versionStream)
    }
    else {
      println "Hey, go and run 'ant fetch-version' to create the ${propsFilename} file"
      System.exit(1)
    }
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


package toxic

import log.Log

import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.xml.DOMConfigurator

/**
 * Command line wrapper for the Toxic program.  Launches a
 * ToxicAgent and configures it with the specified properties file
 * (toxic.properties is used if no file is specified on the command line.)
 */
public class Main {
  private static Logger log = Logger.getLogger(Main.class.name)

  public static void main(String[] args) {
    def exitCode = 1

    def props = loadProperties(args)
    configureLogging(props)

    // Instantiate a new agent and initialize it with the specified prop file.
    def agent = new ToxicAgent()
    agent.init(props)

    // Write the PID to the PID file
    createPIDFile(props.pidFile)

    // Start the clone agent in a new thread.
    def pool = Executors.newFixedThreadPool(1)
    def future = pool.submit(agent)

    // Wait for the external flag to change which tells this
    // program it's time to shutdown toxic.
    boolean issuedShutdown
    while (true) {
      if (!issuedShutdown && shouldShutdown(props.pidFile)) {
        agent.shutdown()
        issuedShutdown = true
      } else {
        try {
          def results = future.get(100, TimeUnit.MILLISECONDS)
          def success = 0
          results.each {
            success += (it.success ? 1 : 0)
          }
          exitCode = (success == results.size() ? 0 : 2)
          // it's finished
          break
        } catch (TimeoutException te) {
        } catch (Throwable t) {
          log.error("Fatal error in Toxic process", t)
          break
        }
      }
    }

    pool.shutdown()

    // Remove the PID file if it hasn't already been removed by an
    // external process requesting us to shutdown.
    deletePIDFile(props.pidFile)

    System.exit(exitCode)
  }

  public static def loadPropertiesFile(ToxicProperties props, def propFile, boolean useClasspath = false) {
    def lg = props?.log ?: log
    lg.debug("Loading properties; propFile=" + propFile + "; useClasspath=" + useClasspath)
    def stream
    try {
      if (propFile instanceof String) {
        if (useClasspath) {
          stream = ClassLoader.getSystemResourceAsStream(propFile) ?: getResourceAsStream("/" + propFile)
        } else {
          def f = new File(propFile)
          if (f.exists()) {
            props.propertiesFile = f
            stream = f.newInputStream()
          }
        }
      } else if (propFile instanceof File && propFile.exists()) {
        props.propertiesFile = propFile
        stream = propFile.newInputStream()
      }

      if (!stream) {
        lg.debug("Properties file is not accessible; missingPropFile=" + propFile)
      } else {
        props.load(stream)
      }
    } catch (Exception e) {
      lg.warn("Failed to load properties; invalidPropFile=" + propFile, e)
    } finally {
      stream?.close()
    }
  }

  public static def loadArgs(String[] args) {
    def stdArgs = []
    def override = [:]
    args.each {
      if (it.startsWith("-")) {
        def key = it[1..-1]
        def value = ""
        if (key.contains("=")) {
          def idx = key.indexOf("=")
          def tmp = key.substring(0, idx)
          if ((idx + 1) < key.size()) value = key.substring(idx + 1)
          key = tmp
        }
        override[key] = value
      } else {
        stdArgs << it
      }
    }

    return [overrides: override, stdArgs: stdArgs]
  }

  public static def loadProperties(String[] args) {
    def argResults = loadArgs(args)
    def stdArgs = argResults.stdArgs
    def override = argResults.overrides

    if (override.containsKey("-?") || override.containsKey("-h") || override.containsKey("--help")) {
      println("Usage: bin/toxic [properties-file] [overrides]")
      println("Arguments")
      println("  properties-file: An optional name of a properties file to load.").
      println("  overrides:       Zero or more hyphen-prefixed key=value pairs to override the")
      println("                   properties specified in the default or named properties file.")
      println()
      println("Ex: bin/toxic toxictest.properties -agentTaskMasterCount=100")
      println("Ex: bin/toxic -tmReps=30")
      println("Ex: bin/toxic mytoxic.properties -tmReps=30 -agentTaskMasterCount=1000")
      System.exit (1)
    }

    def toxicProps = new ToxicProperties()
    loadProperties(toxicProps, stdArgs, override)

    // Optionally load the properties located in parent directories, which means
    // we need to recreate the properties in the new order.
    if (toxicProps.parentProps == "true") {
      def doDirs = []
      toxicProps.keySet().each {
        if(it.startsWith("doDir")) {
          doDirs << toxicProps[it]
        }
      }
      toxicProps.clear()
      loadProperties(toxicProps, stdArgs, override, doDirs)
    }

    // Ensure a properties file was successfully loaded
    if (!toxicProps) {
      log.error("Aborting, no valid properties have been specified and the default toxic.properties are in accessible.")
      System.exit(2)
    }

    return toxicProps
  }

  static void loadProperties(ToxicProperties toxicProps, def stdArgs, def override, def doDirs = []) {
    // Load the default properties from the supplied toxic.properties file
    loadPropertiesFile(toxicProps, "toxic.properties", true)

    // Load users global properties
    loadPropertiesFile(toxicProps, new File(System.getenv()['HOME'], '.toxic/global.properties'))

    // Load from doDir parent properties
    doDirs.each {
      loadParentProperties(toxicProps, it)
    }

    // Load the properties from an optional properties file
    stdArgs.each {
      loadPropertiesFile(toxicProps, it)
    }

    // Apply the overrides
    toxicProps.putAll(override)
  }

  /**
   * Traverse backwards up the directory structure starting from the doDir property value
   * and push each .properties file encountered onto a stack.  When at the root of the
   * file system, pop each property file off the stack and add to/update the working property set.
   */
  public static def loadParentProperties(ToxicProperties props, def doDir) {
    def propFiles = []
    log.debug("Inspecting parent directories for properties files; doDir=" + doDir)
    def f = new File(doDir).canonicalFile
    if (f.exists()) {
      def curDir = f.parentFile
      while (curDir) {
        log.debug("Checking parent directory for properties files; curDir=" + curDir.canonicalPath)
        def unsorted = []
        curDir.eachFile {
          unsorted << it
        }
        def children = unsorted.sort()
        def curPropFiles = []
        for (def propFile : children) {
          if (!propFile.isDirectory() && propFile.name.endsWith(".properties")) {
            curPropFiles << propFile
          }
        }
        propFiles.addAll(0, curPropFiles)
        f = curDir
        curDir = f.parentFile
      }

      propFiles.each {
        loadPropertiesFile(props, it)
      }
    }

    return props
  }

  /**
   * Returns true if the PID file has been deleted.
   */
  protected static boolean shouldShutdown(String filename) {
    return !(new File(filename).exists())
  }

  /**
   * Deletes the specified PID file.
   */
  protected static boolean deletePIDFile(String filename) {
    File f = new File(filename)
    if (f.exists()) {
      f.delete()
    }
  }

  /**
   * Creates the specified PID file containing the process ID.
   */
  protected static void createPIDFile(String filename) {
    new File(filename).text = getPID() + "\n"
  }

  /**
   * Returns the PID of the running program.
   */
  protected static def getPID() {
    def name = ManagementFactory.getRuntimeMXBean().name
    return name?.substring(0, name?.indexOf("@"))
  }

  /**
   * Returns this product's or tool's descriptive title.
   */
  public static String getTitle() {
    return Toxic.genProductVersionString("Toxic")
  }

  public static void configureCustomLogging(ToxicProperties props) {
    def resource = ClassLoader.getSystemResource(props.logConf) ?: getResource(props.logConf)
    if (resource) {
      DOMConfigurator.configure(resource)
    } else {
      println "Failed to locate log4j configuration file in classpath or on filesystem; file=${props.logConf}"
    }    
  }

  public static void configureDefaultLogging() {
    println "Using factory default log4j settings"    
  }

  public static void configureLogging(ToxicProperties props) {
    if (!props.containsKey("logConf")) {
      props.logConf = "log4j.xml"
    }

    if (props.logConf && !props.containsKey("useDefaultLogging")) {
      configureCustomLogging(props)
    } else {
      configureDefaultLogging()
    }

    log.info(getTitle())

    if (props.logLevel) {
      log.getRootLogger().setLevel(Level.toLevel(props.logLevel))
    }

    props.keySet().each {
      String prefix = 'logLevel.'
      if(it.startsWith(prefix)) {
        Log.setLevel(it - prefix, props[it])
      }
    }
  }
}

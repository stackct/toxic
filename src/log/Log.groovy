package log

import groovy.transform.Synchronized

import org.apache.log4j.*
import org.apache.log4j.spi.*
import org.apache.log4j.xml.DOMConfigurator
import org.xml.sax.SAXException

class LogTracker extends Logger {
  def originalLogger
  def entries = new LinkedList()

  LogTracker(originalLogger) {
    super(originalLogger.getName())
    this.originalLogger = originalLogger
  }

  synchronized def reset() {
    entries.clear()
    originalLogger
  }

  boolean isEnabledFor(Priority level) {
    originalLogger.isEnabledFor(level)
  }

  synchronized void log(String name, Priority level,  message, Throwable exception) {
    entries << [level:level, message: message, exception: exception]

    if (entries.size() > 60) entries.remove()
    originalLogger.log(name, level, message, exception)
  }

  synchronized boolean isLogged(String thisMessage, Priority level=null) {
    entries.find { entry -> 
      boolean match = (entry.message.contains(thisMessage))

      if (level) {
        match &= entry.level == level
      }

      match
    }
  }

  synchronized boolean isLogged(String thisMessage, Map values, Priority level=null) {
    def expectedMessage = Log.collectMap(thisMessage, values)
    entries.find { entry ->
      boolean match = (entry.message.contains(expectedMessage))

      if (match && level) {
        match &= entry.level == level
      }

      match
    }
  }

  synchronized boolean isLogged(String thisMessage, Priority level, Class exceptionClass) {
    entries.find { entry -> 
      entry.message.contains(thisMessage) && 
        entry.level == level && 
        entry.exception?.getClass() == exceptionClass
    }
  }
}

public class Log {
  final static int LEVEL_NONE = 6
  final static int LEVEL_ERROR = 5
  final static int LEVEL_WARN = 4
  final static int LEVEL_INFO = 3
  final static int LEVEL_DEBUG = 2
  final static int LEVEL_TRACE = 1
  static boolean simpleLogging

  static String DEFAULT_LOG4J_XML_PATH
  static xml4jConfigPath
  static def scrubPatterns

  static {
    reset()
  }

  static void reset() {
    DEFAULT_LOG4J_XML_PATH = "log4j.xml"  // Will check classpath for this file

    simpleLogging = false
    xml4jConfigPath = { ClassLoader.getSystemResource(DEFAULT_LOG4J_XML_PATH) }
    scrubPatterns = [:]
  }

  Logger log4jLogger
  String name


  Log(String name){
    this.name = name
    log4jLogger = Logger.getLogger(name)
    configure()
  }

  @Synchronized
  protected static void configure() {
    if (!isConfigured()) {
      reconfigure()
    }
  }

  /**
   * Log4j will actually configure itself because we have a log4j.xml in the classpath.
   *
   * The Logger.getLogger(name) call in the new Log(name) constructor actually
   *  initializes log4j using the log4j.xml because that is what log4j looks for.
   *
   * When configure is caused, it is already configured and does NOTHING.
   *
   * Therefore, the configuration done in this class is mostly for when re-configuration is necessary. 
   *
   * It is helpful in utility classes to (like Baseline, Sweep, Prune) so they can have there own logging.
   *
   * As an alternative, we could just let log4j configure itself, and in the shell scripts for those
   *   utilities set -Dlog4j.configuration=log4j-baseline.xml. 
   * 
   * It seems like us having some code that configures log4j for us isn't a bad thing. So, I'm choosing
   *   this way.
   *   
   * To use, put the following in your utility class:
   *     Log.DEFAULT_LOG4J_XML_PATH = 'log4j-baseline.xml'
   *     Log.reconfigure()
   *
   */
  public static reconfigure(){
    def url = xml4jConfigPath()
    BasicConfigurator.resetConfiguration()
    if (url != null) {
      DOMConfigurator.configure(url)
      simpleLogging = false
    } else {
      configureSimpleLogging()
      Logger.getRootLogger().info("log4j.xml configuration file not found; defaulting to simple console logging")
      simpleLogging = true
    }
  }

  public static boolean isUsingSimpleLogging() {
    return simpleLogging
  }

  public static isConfigured() {
    return Logger.getRootLogger().getAllAppenders()?.hasMoreElements()
  }

  /**
   * Configures a very simplistic LOG4J environment where the output will
   * be sent to the console.
   */
  public static void configureSimpleLogging() {
    Properties props = new Properties()
    props.setProperty("log4j.rootLogger", "trace, console")

    props.setProperty("log4j.appender.console", "org.apache.log4j.ConsoleAppender")
    props.setProperty("log4j.appender.console.layout", "org.apache.log4j.PatternLayout")
    props.setProperty("log4j.appender.console.layout.ConversionPattern", "%d [%t] %-5p %c %x - %m%n")
    props.setProperty("log4j.appender.console.Threshold", "trace")

    PropertyConfigurator.configure(props)
  }

  static Log getLogger(Class c){ return getLogger(c.name) }
  static Log getLogger(String name){ return new Log(name) }

  /*
   * Capture stdout and stderr in the same output buffer
   */
  static captureStdOut(closure) {
    def output

    def oldStdOut = System.out
    def oldStdErr = System.err

    def baos = new ByteArrayOutputStream()

    try {
      System.out = new PrintStream(baos)
      System.err = new PrintStream(baos)

      closure()

      baos.flush()
      output = baos.toString()
    }
    finally {
      System.out = oldStdOut
      System.err = oldStdErr
    }

    output
  }

  /**
   * Add an appender that will capture the log data in a string to be returned later.
   * Similar to track except it uses a new appender instead of wrapping the current one.
   * Without closure:
   * - returns capture allowing caller to get hold of text and to close.
   * With closure:
   * - returns text allowing caller to access it after the capture method returns.
   * @param options is commons logger, log4j logger or hash in the form [logger: log, includes: list of strings to include]
   * @param closure optional block called with log capture enabled.
   * @return string with cr
   */
  static capture(options = [:], closure = null) {
    def logger
    def includes

    // Double optional shuffle. Nicer API or just implementation complication?
    if (options instanceof Closure) {
      closure = options
      options = [:]
    }
    else if (options instanceof Map) {
      logger = options["logger"]
      includes = options["includes"]
    }
    else {
      logger = options
    }

    if (logger instanceof log.Log) {
      logger = logger.log4jLogger
    }
    else if (logger instanceof org.apache.log4j.Logger) {
      // Correct type already
    }
    else if (logger != null) {
      throw new IllegalArgumentException("Unsupported logger type: ${logger.getClass()}")
    }

    if (!logger) {
      logger =  Logger.getRootLogger()
    }

    def capture = LogCapture.factory(logger)
    capture.includes = includes
    capture.capture(closure)
  }

  /**
   * Add an appender that will capture the log data in a string to be returned later.
   * Must use a closure to wrap the processing to capture and the output will be
   * use the bar delimited strings to allow the admin client script to convert them back
   * into cr's again.
   * @param options are: [logger: log, includes: list of strings to include]
   * @return string with bar line delimiters
   */
  static captureForAdminResponse(options = [:], closure = null) {
    // Double optional shuffle. Nicer API or just implementation complication?
    if (options instanceof Closure) {
      closure = options
      options = [:]
    }

    def logger = options["logger"]
    def includes = options["includes"]

    def text = new StringBuffer()
    capture(options, closure).eachLine { line ->
      text << "${line}|"
    } 
    text.toString()
  }

  void addAppender(Appender appender) {
    this.log4jLogger.addAppender(appender)
  }
  void removeAppender(Appender appender) {
    this.log4jLogger.removeAppender(appender)
  }

  int getLevel(){
    return getLevel(log4jLogger)
  }

  static int getLevel(Logger llogger){
    switch (llogger.getLevel()) {
      case Level.ERROR: return LEVEL_ERROR
      case Level.WARN: return LEVEL_WARN
      case Level.INFO: return LEVEL_INFO
      case Level.DEBUG: return LEVEL_DEBUG
      case Level.TRACE: return LEVEL_TRACE
      default: return LEVEL_NONE
    }
  }

  void setLevel(level){
    setLevel(log4jLogger, level)
  }

  static void setLevel(Logger llogger, int level){
    switch (level) {
      case LEVEL_ERROR: llogger.setLevel(Level.ERROR); break
      case LEVEL_WARN: llogger.setLevel(Level.WARN); break
      case LEVEL_INFO: llogger.setLevel(Level.INFO); break
      case LEVEL_DEBUG: llogger.setLevel(Level.DEBUG); break
      case LEVEL_TRACE: llogger.setLevel(Level.TRACE); break
      default: llogger.setLevel(Level.OFF);
    }
  }

  static void setRootLevel(int level) {
    setLevel(Logger.rootLogger, level)
  }

  static void setRootLevel(String level) {
    setLevel(Logger.rootLogger, convertLevel(level))
  }

  static int convertLevel(String level) {
    int intLevel
    switch(level?.toLowerCase()) {
      case "error": intLevel = Log.LEVEL_ERROR; break
      case "warn": intLevel = Log.LEVEL_WARN; break
      case "info": intLevel = Log.LEVEL_INFO; break
      case "debug": intLevel = Log.LEVEL_DEBUG; break
      default:
        intLevel = Log.LEVEL_TRACE; break
    }
    return intLevel
  }

  static VALIDATING = true
  static NAMESPACE_AWARE = true

  static formatXml(xml) {
    def formatted = ""

    if (xml) {
      try {
        def parser = new XmlParser(!VALIDATING, !NAMESPACE_AWARE)
        def root = parser.parseText(xml)

        def writer= new StringWriter()
        def printer = new XmlNodePrinter(new PrintWriter(writer))
        printer.setPreserveWhitespace(true)
        printer.print(root)
        formatted = writer.toString()
      }
      catch (SAXException e) {
        formatted = xml
      }
    }

    formatted
  }

  def track(closure) {
    try {
      log4jLogger = new LogTracker(log4jLogger)
      closure(log4jLogger)
    }
    finally {
      log4jLogger = log4jLogger.reset()
    }
  }
  boolean isLogged(message) {
    def logged = false
    if (log4jLogger instanceof LogTracker) {
      logged = log4jLogger.isLogged(message)
    }
    logged
  }

  boolean getError(){ log4jLogger.isEnabledFor(Level.ERROR) }
  boolean getWarn(){ log4jLogger.isEnabledFor(Level.WARN) }
  boolean getInfo(){ log4jLogger.isEnabledFor(Level.INFO) }
  boolean getDebug(){ log4jLogger.isEnabledFor(Level.DEBUG) }
  boolean getTrace(){ log4jLogger.isEnabledFor(Level.TRACE) }

  void error(String message, Throwable ex) {
    logit(name, Level.ERROR, message, ex)
  }

  void error(String message){
    error(message, (Throwable)null)
  }

  void error(String title, Map values) {
    error(collectMap(title,values))
  }

  void error(String title, Map values, Throwable t) {
    error(collectMap(title,values), t)
  }

  void warn(String message, Throwable ex){
    logit(name, Level.WARN, message, ex)
  }

  void warn(String message){
    warn(message, (Throwable)null)
  }

  void warn(String title, Map values) {
    warn(collectMap(title,values))
  }

  void info(String message, Throwable ex){
    logit(name, Level.INFO, message, ex)
  }

  void info(String message){
    info(message, (Throwable)null)
  }

  void info(String title, Map values) {
    info(collectMap(title,values))
  }

  void debug(String message, Throwable ex){
    logit(name, Level.DEBUG, message, ex)
  }

  void debug(String message){
    debug(message, (Throwable)null)
  }

  void debug(String title, Map values) {
    debug(collectMap(title,values))
  }

  void trace(String message, Throwable ex){
    logit(name, Level.TRACE, message, ex)
  }

  void trace(String message){
    trace(message, (Throwable)null)
  }

  void trace(String title, Map values) {
    trace(collectMap(title,values))
  }

  static String collectMap(String title, Map values) {
    new StringBuilder().with { sb ->
      sb.append("${title}; ")
      sb.append(values.collect{k,v -> "${k}=\"${v?.toString()?.replaceAll("\"","\'")}\""}.join('; '))
    }.toString()
  }

  void logit(String name, Level level, String message, Exception ex) {
    if (log4jLogger.isEnabledFor(level)) {
      log4jLogger.log(name, level, scrubKeywords(message), ex)
    }
  }

  /**
   * Prevent apps from unintentionally logging trigger keywords. This can happen if we log a response
   * from a third-party service. Ex: AcmeCorp returns a response to us with "Failure: internal server error".
   * We don't want that to trigger pages on our side so we have ensure that doesn't actually get logged out. 
   */
  String scrubKeywords(String str) {
    if (str != null && scrubPatterns?.size() > 0) {
      scrubPatterns.each { pattern, replacement ->
        str = str.replaceAll(pattern, replacement)
      }
    }
    return str
  }
}


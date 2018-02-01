package process

import groovy.mock.interceptor.*
import java.nio.file.*
import java.lang.management.ManagementFactory

import util.*
import log.Log

/**
 * def process = Process.command("ls")
 * def exitCode = process.start()
 * println process.output
 */
class Process {
  def static log = Log.getLogger(Process)

  /*
   * Allow mock processes to be launched with predictable results.
   * options are:
   * options.output is the the text to be written to stdout
   * options.error is the text to be written to stder
   * options.exitCode is the exit value of the process
   * options.exception is an instance of an exception that is thrown at execute time.
   *
   * Process.mock(output: "abc") { Process.start("echo abc").output == "abc" }
   *
   * Process.mock(exception: new IOException("oops")) { Process.start("ls -la") }
   */
  static mock(options = [:], closure = null) {
    if (options instanceof Closure) {
      closure = options
      options = [:]
    }

    def actual = [
      output: "",
      error: "",
      exitCode: 0,
    ] + options
    
    boolean startedProcess = false

    def output

    def mockThread = new MockFor(Thread)
    mockThread.ignore.join() { timeoutMillis -> }
    def mockThreadInstance = mockThread.proxyInstance()

    def processMock = new MockFor(java.lang.Process)
    processMock.ignore.asBoolean() { true }

    processMock.ignore.waitFor() { pause, timeUnit -> 
      if (options.expectedPause)    assert options.expectedPause == pause, "waitFor timeout should be ${options.expectedPause}"
      if (options.expectedTimeUnit) assert options.expectedTimeUnit == timeUnit, "waitFor timeUnit should bn ${options.expectedTimeUnit}"

      options.waitForExitCode ?: true 
    }

    processMock.ignore.consumeProcessOutputStream() { outputStream -> 
      outputStream << actual.output
      mockThreadInstance
    }
    processMock.ignore.consumeProcessErrorStream() { errorStream -> 
      errorStream << actual.error
      mockThreadInstance
    }

    processMock.ignore.exitValue() {
      actual.exitCode
    }

    processMock.ignore.getErrorStream() { [close: {->}] }
    processMock.ignore.getInputStream() { [close: {->}] }
    processMock.ignore.getOutputStream() { [close: {->}] }

    def processProxy = processMock.proxyInstance()

    def builderMock = new MockFor(ProcessBuilder)
    options.directory ? builderMock.ignore.directory() { dir -> options.directory(dir) } : builderMock.ignore.directory() 
    builderMock.ignore.command()
    builderMock.ignore.redirectErrorStream()
    builderMock.ignore.inheritIO()
    builderMock.ignore.redirectOutput() { file -> output = file }
    builderMock.ignore.start() { 
      if (options.exception) throw options.exception

      startedProcess = true

      processProxy
    }

    processMock.use {
      builderMock.use {
        closure()
      }
    }

    assert startedProcess == !(options.exception)
  }

  /*
   * Current process pid
   */
  static pid() {
    def pid = "0"

    def name = ManagementFactory.runtimeMXBean.name
    def group = name =~ /([0-9]+)@.*/
    if (group.matches()) {
      pid = name?.substring(0, name?.indexOf("@"))
    }

    pid
  }

  /*
   * Find the root dir of a running process based on a relative offset from th running classes.
   * Pass in the relative offset, and this method will resolve the path based on the location if this class file.
   * Example:
   *   Process.class is in jar "/root/lib/common.jar"
   *
   *   Process.rootDir("..") == "root"
   */
  static rootDir(relativePath = ".") {
    def clazz = Process.class
    def domain = clazz.protectionDomain
    def source = domain.codeSource
    def location = source.location
    def uri = location.toURI()

    def path = new File(uri).path  
    Paths.get(path, relativePath).toString()
  }

  /*
   * Convenince method for one time processes. Waits for process to complete.
   * Process.start("ls -la").output == "abc"
   * Process.start(["ls", "-la"]).output == "abc"
   *
   * Override process environment settings:
   *   Process.start("ls -la", [
   *     currentDir: "/tmp",
   *     output: new MyOutputStream(),
   *     timeout: 23,
   *     timeoutUnit: java.util.concurrent.TimeUnit.MINUTES
   *   ])
   */
  static start(command, options = [:]) {
    options = [
      currentDir: ".",
      output: new ProcessOutputString()
    ] + options

    def process = Process.command(command, options.currentDir)
    process.output = options.output

    if (options.timeout)     process.timeout     = options.timeout
    if (options.timeoutUnit) process.timeoutUnit = options.timeoutUnit

    process.start()
    process
  }

  /*
   * Construct a process, based on a command, that can be started.
   */
  static command(command, currentDir = ".") {
    new Process(command, currentDir)
  }

  private command
  private currentDir
  @Delegate ProcessOutput output = new ProcessOutputString()

  def timeout
  def timeoutUnit

  private Process(command, currentDir = ".") {
    this.command = command
    this.currentDir = currentDir
  }

  /*
   * Launch and block until it is complete.
   */
  int start() {
    def running = ProcessRunning.factory(command, output, currentDir)

    if (timeout && timeoutUnit) {
      running.waitFor(timeout, timeoutUnit)
    }
    else {
      running.waitForever()
    }
  }

  /*
   * Launch in the background and optionally, waitFor later on.
   */
  def spawn() {
    ProcessRunning.factory(command, output)
  }

  String toString() {
    command
  }
}


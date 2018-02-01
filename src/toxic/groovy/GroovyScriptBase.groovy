
package toxic.groovy

import toxic.Toxic
import toxic.Sanitizer
import org.apache.log4j.*

/*
 * Methods added to this class are available in each of the GroovyTask scripts.
 */
abstract class GroovyScriptBase extends Script {
  static Logger slog = Logger.getLogger(GroovyScriptBase.class.name)

  private static class ThreadLocalBuffer extends ThreadLocal<StringBuffer> {
    protected StringBuffer initialValue() { new StringBuffer() }
  }
  static ThreadLocal outThreadLocal = new ThreadLocalBuffer()
  static ThreadLocal errThreadLocal = new ThreadLocalBuffer()
  def out
  def err
  def execExitCode
  
  public Logger getLog() {
    return binding?.getVariable("log") ?: this.slog
  }  

  def fetchLogger(String logfile) {
    def logger

    if (logfile) {
      logger = Logger.getLogger("exec-" + logfile)
      logger.setAdditivity(false)
      logger.removeAllAppenders()
      logger.addAppender(new FileAppender(new PatternLayout("%d [%t] %-5p %c %x- %m%n"), logfile))
    } else {
      logger = log
    }

    return logger
  }

  def getOutputBuffer() {
    outThreadLocal.get()
  }

  def getErrorBuffer() {
    errThreadLocal.get()
  }

  def consumeStream(startTime, stream, closure = null) {
    int avail = 0
    try {
      avail = stream.available()
    } catch (IOException e) {
      // stream closed
    }
    if (avail > 0) {
      def bytes = new byte[avail]
      stream.read(bytes, 0, avail)
      def elapsedMs = System.currentTimeMillis() - startTime
      def buf = new String(bytes, 'UTF-8')
      if(closure) {
        closure(buf, elapsedMs)
      }
    }
  }

  def monitorProc(logger, proc, logOutput=true, timeoutSecs = 2 * 60 * 60) {
    def finished
    def startTime = System.currentTimeMillis()
    def sem = new Object()
    def outBuf = getOutputBuffer()
    def errBuf = getErrorBuffer()

    def createOutputHandler = { tag, retainer ->
      def outputHandler = { buf, elapsedMs ->
        if(retainer != null) {
          // Avoid accruing the same log data that has already been captured for those inputstreams
          // that do not behave like a true stream.
          buf -= retainer.toString()
          retainer.append(buf)
        }
        if(logOutput) {
          logger.info("Process ${tag}; elapsedMs=${elapsedMs}\n${buf}")
        }
      }
      return outputHandler
    }

    def stdoutHandler = createOutputHandler("stdout", outBuf)
    def stderrHandler = createOutputHandler("stderr", errBuf)

    def threadName = "exec-output-"+Thread.currentThread().getName()
    def outputThread = Thread.start(threadName, {
      while (!finished) {
        synchronized(sem) { sem.wait(1000) }
        consumeStream(startTime, proc.inputStream, stdoutHandler)
        consumeStream(startTime, proc.errorStream, stderrHandler)
      }
      consumeStream(startTime, proc.inputStream, stdoutHandler)
      consumeStream(startTime, proc.errorStream, stderrHandler)
    })
    proc.waitForOrKill(timeoutSecs*1000) // Kill process if not completed in the specified time
    finished = true
    synchronized(sem) { sem.notify() }
    outputThread.join()
    def result = proc.exitValue()
    logger.info("Shell command finished; result=${result}; timeoutSecs=${timeoutSecs}; elapsedMs=${System.currentTimeMillis() - startTime}")
    return result
  }
  
  def clearBuffer(buffer) {
    buffer?.delete(0,buffer.size())
  }
  
  def resetBuffers() {
    clearBuffer(getOutputBuffer())
    clearBuffer(getErrorBuffer())
  }
  
  def exec(cmd, logfile = null) {
    def logger = fetchLogger(logfile)

    // Can use these safely only in single-threaded scripting, for multi-threading
    // always use the getOutputBuffer/getErrorBuffer methods.
    out = out != null ? out : getOutputBuffer()
    err = err != null ? err : getErrorBuffer()
    resetBuffers()

    logger.info("Executing shell command; cmd=${Sanitizer.sanitize(cmd)}")
    execExitCode =  monitorProc(logger, Runtime.runtime.exec(cmd))
    return execExitCode
  }

  def execWithEnv(cmdAndArgs, envVars = [:], timeoutSecs = 300, workingDir = null, logfile = null) {
    _execWithEnv(cmdAndArgs, envVars, true, timeoutSecs, workingDir, logfile)
  }

  def execWithEnvNoLogging(cmdAndArgs, envVars=[:], timeoutSecs = 300, workingDir = null, logfile = null) {
    _execWithEnv(cmdAndArgs, envVars, false, timeoutSecs, workingDir, logfile)
  }

  def _execWithEnv = { cmdAndArgs, envVars=[:], logOutput=true, timeoutSecs=300, workingDir=null, logfile=null ->
    def logger = fetchLogger(logfile)

    // Can use these safely only in single-threaded scripting, for multi-threading
    // always use the getOutputBuffer/getErrorBuffer methods.
    this.out = out != null ? out : getOutputBuffer()
    this.err = err != null ? err : getErrorBuffer()
    resetBuffers()

    def pb = new ProcessBuilder()
    def cmdAndArgsArray = new ArrayList()
    cmdAndArgsArray.addAll(cmdAndArgs as String[])

    if (workingDir) {
      pb.directory(new File(workingDir).getAbsoluteFile())
    }
    pb.command(cmdAndArgsArray)
    def env = pb.environment()
    env << envVars

    logger.info("Executing shell command; cmd=${Sanitizer.sanitize(cmdAndArgs.join(' '))}")
    execExitCode = monitorProc(logger, pb.start(), logOutput, timeoutSecs)
    return execExitCode
  }

  def fail = { reason ->
    throw new Exception(reason)
  }

  def version() {
    Toxic.genProductVersionString("Toxic")
  }
}

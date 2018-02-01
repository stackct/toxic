package process

import java.util.concurrent.*

import util.*
import log.Log

class ProcessRunning {
  def static log = Log.getLogger(ProcessRunning)

  static factory(String command, output, currentDir = ".") {
    factory(splitArgs(command), output, currentDir)
  }

  static factory(List command, output, currentDir = ".") {
    try {
      def builder = new ProcessBuilder(command as String[])
      builder.directory(new File(currentDir))

      output.setBuilder(builder)

      def process = builder.start()

      log.debug("run start ", [command:command])
      new ProcessRunning(command, process, output, 0)
    }
    catch (IOException e) {
      log.debug("run finish", [command:command, failed:e.message])
      output.writeLine("${command.first()}: command not found")
      new ProcessRunning(command, null, output, 1)
    }
  }

  // @TODO support value quoting.
  private static splitArgs(command) {
    def strings = command.split(/[\t ]+/)
    def justCommand = strings[0]
    def args = strings.size() > 1 ? strings[1..-1] : []
    [justCommand, *args]
  }

  def command
  def process
  @Delegate ProcessOutput output
  def exitValue = 0

  private ProcessRunning(command, process, output, exitValue) {
    this.command = command
    this.process = process
    this.output = output
    this.exitValue = exitValue
  }

  int waitForever() {
    waitFor(1000, TimeUnit.DAYS)
  }

  int waitFor(timeout = 5000, timeoutUnit = TimeUnit.MILLISECONDS) {
    if (!process) return exitValue

    boolean exited = waitForProcessOutput(process, output.stream, output.stream, timeout, timeoutUnit)
    if (!exited) {
      throw new InterruptedException("timeout, process not finished yet")
    }

    exitValue = process.exitValue()

    if (log.trace) {
      log.trace("run finish", [command:command, exitCode:exitValue, output:output.output])
    }
    else if (log.debug) {
      log.debug("run finish", [command:command, exitCode:exitValue, output:output.output.size() > 0])
    }

    exitValue
  }

  // Copied from ProcessGroovyMethods.java line 241
  // Then updated to add timeout to thread join and waitFor
  private boolean waitForProcessOutput(process, output, error, timeout, timeoutUnit) {
    boolean exited = false

    def tout = process.consumeProcessOutputStream(output);
    def terr = process.consumeProcessErrorStream(error);

    // Wait for background threads to finish reading process streams before waiting for pid.
    def pause = timeoutUnit.toMillis(timeout)
    try { tout.join(pause); } catch (InterruptedException ignore) {}
    try { terr.join(pause); } catch (InterruptedException ignore) {}

    // Wait for background process
    try { exited = process.waitFor(timeout, timeoutUnit); } catch (InterruptedException ignore) {}

    try { process.errorStream.close(); } catch (IOException ignore) {}
    try { process.inputStream.close(); } catch (IOException ignore) {}
    try { process.outputStream.close(); } catch (IOException ignore) {}

    exited
  }
}


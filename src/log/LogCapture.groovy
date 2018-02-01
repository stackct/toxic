package log

import org.apache.log4j.*
import org.apache.log4j.spi.*
import org.apache.log4j.varia.*

class LogCapture {
  static factory(logger) {
    new LogCapture(logger)
  }

  def logger
  def includes = []

  private LogCapture(logger) {
    this.logger = logger
  }

  def capture(closure = null) {
    if (closure) captureWithClosure(closure)
    else captureExpectingClose()
  }

  private captureWithClosure(closure) {
    def captured

    try {
      captured = createCapture()

      closure()

      captured.text
    }
    finally {
      captured.close()
    }
  }

  private captureExpectingClose() {
    createCapture()
  }

  private createCapture() {
    def writer = new StringWriter()
    def appender = new WriterAppender(new PatternLayout("%p: %m\n"), writer)

    // limit messages to this writer to the current thread.
    appender = new StickyThreadAppender(appender)

    // Allow only certain words to be logged
    if (includes) {
      includes.each { includeText ->
        def filter = new StringMatchFilter()
        filter.stringToMatch = includeText

        appender.addFilter(filter)
      }
      appender.addFilter(new org.apache.log4j.varia.DenyAllFilter())
    }

    this.logger.addAppender(appender)

    new LogCaptured(logger, appender, writer)
  }
}

class LogCaptured {
  def logger
  def appender
  def writer
  
  def LogCaptured(logger, appender, writer) {
    this.logger = logger
    this.appender = appender
    this.writer = writer
  }

  def getText() {
    this.writer.toString()
  }

  def close() {
    this.logger.removeAppender(this.appender)
    this.writer.close()
  }
}

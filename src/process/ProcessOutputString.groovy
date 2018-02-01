package process

class ProcessOutputString extends ProcessOutput {
  private writer

  ProcessOutputString(writer = new StringWriter()) {
    this.writer = writer
  }

  def setProcess(process) {
    process.consumeProcessOutput(writer, writer)
  }

  def setBuilder(builder) {
    builder.redirectErrorStream(true)
  }

  def writeLine(text) {
    writer << text
  }

  def getStream() {
    writer
  }

  def getOutput() {
    writer.toString()
  }
}


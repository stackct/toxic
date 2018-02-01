package process

import util.*

class ProcessOutputFile extends ProcessOutput {
  private fileName
  private process
  private logFile
  private logWriter

  ProcessOutputFile(fileName) {
    this.fileName = fileName

    logFile = new File(fileName)
  }

  def setProcess(process) {
    this.process = process
  }

  def setBuilder(builder) {
    def writableFileName = chooseFile(fileName)

    builder.redirectErrorStream(true)
    builder.redirectOutput(new File(writableFileName))
  }

  def writeLine(text) {
    writer() << text
  }

  def getStream() {
    writer()
  }

  def getOutput() {
    logWriter?.flush()
    new File(fileName).text
  }

  private chooseFile(name) {
    def chosenFileName

    def alternativeFileName
    while (!chosenFileName) {
      def dateTimeSuffix = DateTime.format(DateTime.now(), "yyyyMMdd.HHmmss")
      alternativeFileName = "${name}.${dateTimeSuffix}.log"

      def fileExists = new File(alternativeFileName).exists()
      if (!fileExists) {
        chosenFileName = alternativeFileName
      }
      else {
        // Avoid multi-process race conditions to a unique filename.
        System.sleep(new Random().nextInt(5) * 100)
      }
    }
    
    chosenFileName
  }

  private writer() {
    logWriter = logWriter ?: logFile.newWriter()
  }
}

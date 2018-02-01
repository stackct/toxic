package process

abstract class ProcessOutput {
  abstract def setProcess(process)
  abstract def setBuilder(builder)
  abstract def writeLine(text)
  abstract def getOutput()
}


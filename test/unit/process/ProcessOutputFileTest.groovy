package process

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import util.*

class ProcessOutputFileTest {
  @Test
  void should_write_through_builder() {
    def writer = new StringWriter()

    def fileMock = new MockFor(File)
    fileMock.ignore.exists() { false }
    fileMock.ignore.newWriter() { writer }

    def output
    fileMock.use {
      output = new ProcessOutputFile("filename")
      output.builder = [redirectErrorStream: { mergeStreams ->}, redirectOutput: { file -> }]

      output.writeLine("hello world")
    }

    writer.flush()
    assert writer.toString() == "hello world"
  }

  @Test
  void should_choose_filename_that_is_writable_and_keep_trying_until_it_finds_one() {
    checkChosenFileName("filename.11111213.141516.log", [ false ])
    checkChosenFileName("filename.11111213.141517.log", [ true, false ])
    checkChosenFileName("filename.11111213.141518.log", [ true, true, false ])
  }

  private checkChosenFileName(expectedFileName, fileExistsSequence) {
    def expectedFileExists = fileExistsSequence as Queue
    def fileMock = new MockFor(File)
    fileMock.ignore.exists() { 
      def exists = expectedFileExists.poll()
      exists = exists != null ? exists : false
      exists
    }

    def slept = false
    System.metaClass.static.sleep = { long millis -> slept = true }

    def chosenFile
    try {
      DateTime.mock("1111-12-13 14:15:16.017", "1111-12-13 14:15:17.017", "1111-12-13 14:15:18.017").use {
        fileMock.use {
          def output = new ProcessOutputFile("filename")
          output.builder = [
            redirectErrorStream: { mergeStreams ->}, 
            redirectOutput: { file -> 
              chosenFile = file
            }
          ]
        }
      }
    }
    finally {
      System.metaClass = null
    }

    assert chosenFile.name == expectedFileName
    assert slept || fileExistsSequence.size() == 1, "should sleep before retrying guessing a filename"
  }
}


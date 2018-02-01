
package toxic.groovy

import toxic.TaskResult

import toxic.Task
import org.apache.commons.io.FilenameUtils
import org.apache.log4j.Logger

import javax.lang.model.SourceVersion

public class GroovyTask extends Task {
  protected static Logger slog = Logger.getLogger(GroovyTask.class.name)

  public List<TaskResult> doTask(def memory) {
    def result = GroovyEvaluator.eval(input, memory, scriptFileName(memory))
    if(result instanceof TaskResult) {
      result = [ result ]
    }
    // If result is a List, gather results which are TaskResult objects
    if(result instanceof List) {
      result = result.findAll { it instanceof TaskResult }
      if(result.empty) { result = null }
    } else {
      result = null
    }

    return result
  }

  // Return a file name which can be used to identify the script (in stacktraces, etc).
  // Attempts to derive the name from task properties. Returns null if not found.
  String scriptFileName(memory) {

    def family = memory.taskFamily
    String name = memory.taskName
    def fileName = null
    if(name) {
      // Remove the file extension (so we can clean up the rest of the name)
      def ext = FilenameUtils.getExtension(name)
      name = FilenameUtils.removeExtension(name)
      // Prefix with the (sanitized) task family
      fileName = family ? family.replaceAll("/","__") + "__"  : ""
      fileName += name
      // Replace dashes and periods with underscores
      fileName = fileName.replaceAll("[-.]","_")
      // Can't start with a digit
      if(fileName.charAt(0).isDigit()) {
        fileName = "_"+fileName
      }
      // Put the extension back on
      if(ext) {fileName += ".${ext}"}
    }

    // Verify that the generated name is a valid class name
    if(fileName && !SourceVersion.isName(fileName-".groovy")) {
      log.info("Generated script class name is not valid, ignoring: '${fileName}'")
      fileName = null
    }

    return fileName
  }
}

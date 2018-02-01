
package toxic.dir

import toxic.CompareTask
import toxic.TaskResult
import groovy.io.FileType
import org.apache.log4j.Logger

public class FileTask extends CompareTask {
  protected static Logger slog = Logger.getLogger(FileTask.class.name)

  protected void initFile(File input) {
    if (!(input instanceof File) || !input.exists() || input.isDirectory()) {
      throw new IllegalArgumentException("Specified input is not a valid file")
    }

    if (!input.name.contains("_1.")) {
      throw new IllegalArgumentException("Specified input filename does not conform to the required '*_1.*' naming convention")
    }
  }

  public void init(def input, def props) {
    super.init(input, props)

    initFile(input)
  }

  public List<TaskResult> doTask(def memory) {
    def control = replace(input.text)

    def prefix = input.name.substring(0, input.name.indexOf("_1."))
    def currentFilename
    try {
      input.parentFile.eachFileMatch(FileType.FILES, ~"${prefix}_[2-9].*") {
        currentFilename = it.name
        def test = replace(it.text)
        validate(test, control, memory)
      }
    } catch (Throwable t) {
      log.error("Comparison failure against; currentFilename=" + currentFilename)
      throw t
    }
  }

  protected transmit(request, def memory) {
  }
}

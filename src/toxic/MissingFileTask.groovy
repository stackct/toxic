package toxic

/**
 * Task class used when the requested task file does not exist (eg: an invalid link)
 */
class MissingFileTask extends Task {

  @Override
  List<TaskResult> doTask(Object memory) {
    def msg= "Task file '${name}' does not exist for task: family: ${family}, name: ${name} "
    log.error(msg)
    def result = new TaskResult(getId(memory),family, name, "Missing File")
    result.success = false
    result.error = new FileNotFoundException(msg)
    [ result ]
  }
}

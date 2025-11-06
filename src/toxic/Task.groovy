
package toxic

import org.apache.log4j.Logger
import org.codehaus.groovy.runtime.StackTraceUtils
import toxic.dsl.MessageCollector
import toxic.color.Color

public abstract class Task {
  protected static Logger slog = Logger.getLogger(Task.class.name)
  def input
  def props
  def shutdown

  public Logger getLog() {
    return props?.log ?: this.slog
  }

  public void init(def input, def props) {
    this.props = props
    this.input = input
  }

  public String getFamily() {
    def family = ""
    if (props?.doLastPath) {
      family = props.doLastPath
    } else {
      if (input instanceof File) {
        family = input.getParentFile()?.name
      }
    }
    return family
  }

  public String getName() {
    def name = input.toString()
    if (input instanceof File) {
      name = input.name
    } else {
      if (name.size() > 32) {
        name = name[0..32]
      }
      if (name.contains("\n")) {
        name = name.substring(0, name.indexOf("\n"))
      }
    }
    return name
  }

  public String getId(def memory) {
    return "" + memory.tmId + "-" + memory.tmRep + "-" + memory.taskId
  }

  public abstract List<TaskResult> doTask(def memory)

  public List<TaskResult> execute(def memory) {
    def id = getId(memory)
    def overallResult = new TaskResult(id, family, name, this.class.name)
    overallResult.success = true
    def results = [ overallResult ]

    memory.taskName = name
    memory.taskFamily = family

    memory.taskIterations = memory.taskIterations ? memory.taskIterations : 1

    def executingMessage = "Executing task; family=$family; name=$name; tmId=$memory.tmId; tmRep=$memory.tmRep;taskId=$memory.taskId; taskIterations=$memory.taskIterations"
    log.debug(executingMessage)
    overallResult.addMessage(executingMessage)
    def iters = new Integer(memory.taskIterations)
    memory.uniqueId = System.currentTimeMillis() + "." + id

    for (memory.taskIteration = 0; new Integer(memory.taskIteration) < iters; memory.taskIteration++) {
      try {
        def mergeResults = doTask(memory)
        if (log.isDebugEnabled()) log.debug("iteration ${memory.taskIteration} results: ${mergeResults}")
        if (mergeResults) {
          results.addAll(mergeResults)
        }
      } catch(Throwable t) {
        def errMsg = t.message?.take(new Integer(props.maxErrorLength ?: 500))
        def result = new TaskResult(id, family, name, this.class.name)
        result.error = errMsg
        result.errorType = t.class.name
        result.success = false
        overallResult.success = false
        if(!overallResult.error) {
          overallResult.error = errMsg
        }
        if(!overallResult.errorType) {
          overallResult.errorType = result.errorType
        }
        // Add an error result if more than one iteration and continuing iterations, otherwise the
        // overall result will hold the single error
        if(iters > 1 && !TaskResult.shouldAbort(memory, results.drop(1) + result)) {
          results << result
        }

        var taskFailed = Color.failure(props, "Task failed")
        var messageFailed = Color.failure(props, result.error)

        def message = "${taskFailed} family='$family'; name='${name}'; iteration='${memory.taskIteration}'; exception='${result.errorType}'; message=${messageFailed}"
        log.error(message)

        MessageCollector.add(message)
        if (!(t instanceof AssertionError) && !(t instanceof ValidationException)) {
          log.error("Error stack trace", t)
          MessageCollector.add("Error stack trace")
          StringWriter sw = new StringWriter()
          PrintWriter pw = new PrintWriter(sw)
          StackTraceUtils.extractRootCause(t)
          t.printStackTrace(pw)
          MessageCollector.add(sw.toString());
        } else {
          log.debug("Task failure stack trace", t)
        }

        def lastRequest = "Diagnostics - Request\n${memory.lastRequest}"
        log.warn(lastRequest)
        MessageCollector.add(getHttpValues(memory))
        MessageCollector.add(lastRequest)
        def lastResponse = "Diagnostics - Response\n${memory.lastResponse}"
        log.warn(lastResponse)
        MessageCollector.add(lastResponse)
      }

      if (memory.taskIteration == null) {
        log.warn("Aborting task execution loop due to null taskIteration property; ensure groovy script does not clear the memory map")
        break
      }

      if (TaskResult.shouldAbort(memory, results)) {
        break
      }

      if (shutdown) break
    }

    if (TaskResult.areAllSuccessful(results)) {
      overallResult.success = true
    }
    overallResult.mark()

    if(log.isDebugEnabled()) log.debug("Task Results: ${results}")

    return results
  }

  String getHttpValues(Map<String,Object> memory){
    String values = ""
    memory.findAll {k,v -> k.startsWith("http.header")}.forEach {k,v -> values += "\n\t${k}: ${v}"}
    return values
  }

  int getIntProperty(def memory, String property) {
    try {
      return new Integer(memory[property])
    } catch (Exception e) {
      log.debug("${property} property is not set, defaulting to 0; error=${e.message}")
    }
    return 0
  }
}

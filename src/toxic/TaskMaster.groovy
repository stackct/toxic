
package toxic

import toxic.groovy.GroovyEvaluator
import java.util.concurrent.Callable
import org.apache.log4j.Logger

/**
 * Runs through a task list and executes all encountered tasks.
 */
public class TaskMaster implements Callable {
  protected static Logger slog = Logger.getLogger(TaskMaster.class.name)
  private def props
  private def id
  private transient boolean shutdown = true

  private List<TaskResult> results

  public TaskMaster() {
    this.results = new ArrayList<TaskResult>()
  }

  protected Logger getLog() {
    return props?.log ?: this.slog
  }

  public List<TaskResult> getResults() {
    this.results
  }

  /**
   * Initializes with the specified set of configuration properties.
   */
  public void init(def props, def id) {
    this.id = id
    this.props = props.clone()
    this.props.tmId = props.tmId ?: id
    if (props.tmPropFilePrefix != null) {
      def tmPropFile = props.tmPropFilePrefix.toString() + id + ".properties"
      Main.loadPropertiesFile(this.props, tmPropFile)
    }
  }

  /**
   * Begins the task mastering process.
   */
  public List<TaskResult> call() {
    try {
      setup()

      def reps = Long.parseLong(props.tmReps)
      def count = 0
      while (count < reps && !shutdown) {
        if (count > 0 && props.tmRepPauseMillis?.toInteger() > 1) {
          pause(props.tmRepPauseMillis)
        }

        doRep(count++)

        if (TaskResult.shouldAbort(props, results)) {
          break
        }

        if (props.tmDiscardRepResults == "true") {
          results.clear()
        }
      }

      teardown()
    } catch (Exception e) {
      log.error(e.message, e)
      def result = new TaskResult("taskMasterException", "TaskMaster", "Unexpected exception", this.class.name)
      result.success = false
      result.setError(e.toString())
      results << result
    }
    failIfNoResults(results)
    return results
  }


  protected void failIfNoResults(def results) {
    if (!results && props?.tmDiscardRepResults != "true") {
      def result = new TaskResult("failIfNoResults", "TaskMaster", "Missing task results", this.class.name)
      result.success = false
      results << result
    }        
  }

  /**
   * Instantiate the organizer which will traverse the
   * hierarchy of tasks and execute each one.
   */
  protected void doRep(int repNum) {
    def memory = props.clone()
    memory.tmRep = repNum
    memory.taskId = 0
    def taskOrganizer = initModule("tmOrganizerClass", memory)
    while (taskOrganizer.hasNext()  && !shutdown) {
      if (memory.taskId > 0 && memory.tmTaskPauseMillis?.toInteger() > 1) {
        pause(memory.tmTaskPauseMillis)
      }
      def task = taskOrganizer.next()

      def taskResults = task.execute(memory)
      synchronized(results) {
        results.addAll(taskResults)
      }
      if (TaskResult.shouldAbort(props, results)) {
        log.info("Aborting task master due to task failure")
        break
      }
      memory.taskId++
    }
    memory.clear()

    // return repResults
  }

  /**
   * Initializes an implementation of the TaskMaster interface.  The implementation
   * classes are specified in the configuration properties.
   */
  public def initModule(String classProperty, def memory) {
    Class c = memory.resolveClass(classProperty)
    if (c == null) {
      throw new IllegalArgumentException("Missing classname for property; classProperty=" + classProperty)
    }
    def module = c.newInstance()
    module.init(memory)
    return module
  }

  protected synchronized pause(def delay) {
    log.info("Pausing for ${delay}ms")
    wait(Long.parseLong(delay.toString()))
  }

  protected synchronized void setup() {
    props.tmResults = results
    shutdown = false

    // Init this task master's groovy shell
    GroovyEvaluator.eval("def noop=true", props)
  }

  protected synchronized void teardown() {
    shutdown = true
  }

  /**
   * Shuts down this datastore interface by closing any remaining resources.
   * Do not continue to use this object after invoking shutdown.
   */
  public synchronized void shutdown() {
    log.debug("Shutting down task master")
    shutdown=true
    notifyAll()
  }

  public boolean isShutdown() {
    return shutdown
  }

  public static void walkResults(def taskMasters, def taskMasterResultIndex, Closure closure) {
    taskMasters?.findAll{ it.results }.each {
      def idx = taskMasterResultIndex[it] ?: 0
      // Clone the results list, otherwise it may be modified while we are iterating over it
      def tmResults = []
      synchronized(it.results) {
        tmResults = it.results.clone()
      }
      int size = tmResults.size()
      if (size > idx) {
        tmResults[idx..(size-1)].each { r ->
          closure(r)
        }
        taskMasterResultIndex[it] = size
      }
    }
  }
}

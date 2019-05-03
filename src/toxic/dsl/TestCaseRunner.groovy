package toxic.dsl

import log.Log
import toxic.AbortExecutionException
import toxic.Task
import toxic.TaskMaster
import toxic.TaskResult
import toxic.dir.DirItem
import toxic.dir.DirOrganizer

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TestCaseRunner implements Callable<TestCaseRunner> {
  private final static Log log = Log.getLogger(this)
  private TaskMaster taskMaster
  private TestCase testCase
  private Step step
  private ConfigObject props
  private List<TaskResult> results
  private Exception error

  private TestCaseRunner(TaskMaster taskMaster, TestCase testCase, ConfigObject props) {
    this.taskMaster = taskMaster
    this.testCase = testCase
    this.props = props
    this.props.testCase = testCase
    this.props.step = new StepOutputResolver(props)
    this.props.var = new VariableResolver(props)
    this.results = new ArrayList<TaskResult>()
  }

  static List<TaskResult> run(TaskMaster tm, ConfigObject props) {
    def results = new ArrayList<TaskResult>()
    if(!props.testCases) {
      return results
    }

    def futures = []
    def pool = Executors.newFixedThreadPool(Integer.parseInt(props['pickle.testCaseThreads']))
    props.testCases.each { tc ->
      futures << pool.submit(new TestCaseRunner(tm, tc, props.clone()))
    }

    def finishedFutures = []
    TestCaseRunner runner = null
    while (!runner?.error && finishedFutures.size() < futures.size()) {
      futures.find {
        if (!finishedFutures.contains(it)) {
          try {
            runner = it.get(20, TimeUnit.MILLISECONDS)
            results.addAll(runner.results)
            finishedFutures << it
            return runner.error
          }
          catch(TimeoutException to) { }
        }
      }
    }

    pool.shutdownNow()
    try {
      log.info("Waiting for in flight test cases to complete")
      pool.awaitTermination(60, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      log.error("Failed to properly shutdown test case pool", e)
    }

    if(runner?.error) {
      def values = [testCase: runner.testCase.name, step: runner.step.name]
      if(runner.error.message) {
        values.reason = runner.error.message
      }
      boolean logStackTrace = true
      if (runner.error instanceof AbortExecutionException) {
        TaskResult t = TaskResult.getFailedTask(runner.results)
        values.family = t.family
        values.name = t.name
        values.message = t.error ?: t.errorType
        logStackTrace = false
      }
      log.error("Task failed", values, logStackTrace ? runner.error : null)
    }

    return results
  }

  @Override
  TestCaseRunner call() {
    log.info("Starting test case; test=${props.testCase.name}")
    def success = false
    try {
      testCase.stepSequence.eachWithIndex { seq, stepIndex ->
        props.stepIndex = stepIndex
        step = seq.step
        executeStep(step)
      }
      executeAssertions()
      success = true
    }
    catch(Exception e) {
      error = e
    }
    finally { log.info("Finished test case; test=${props.testCase.name}; success=${success}") }

    return this
  }

  void executeStep(Step step) {
    preStepExecution(step)

    Function fn = props.functions[step.function]
    if (!fn?.path) return // step refers to a higher order function

    executeDirItem(new DirItem(fn.path))

    postStepExecution(step)
    if(step.lastStepInSequence)
    {
      log.info("Completing step sequence; test=${props.testCase.name}; step=${step.parentStep.name}; fn=${step.parentStep.function}")
      postStepExecution(step.parentStep)
    }
  }

  void executeAssertions() {
    log.info("Executing assertions; test=${props.testCase.name}")
    def resolver = { contents ->
      def interpolatedContents = ''<<''
      contents.eachLine { interpolatedContents << Step.interpolate(props, it) + '\n' }
      interpolatedContents.toString()
    }
    executeDirItem(new DirItem(testCase.assertionFile(new File("noop"), resolver)))
  }

  void preStepExecution(Step step) {
    props.push()
    log.info("Executing step; test=${props.testCase.name}; step=${step.name}; fn=${step.function}")
    step.copyArgsToMemory(props)
  }

  void executeDirItem(DirItem dirItem) {
    DirOrganizer organizer = new DirOrganizer(props: props, dirItem: dirItem)
    while (organizer.hasNext() && !taskMaster.shutdown()) {
      Task task = organizer.next()
      def taskResults = task.execute(props)
      results.addAll(taskResults)
      if (TaskResult.shouldAbort(props, taskResults)) {
        throw new AbortExecutionException()
      }
      props.taskId++
    }
  }

  void postStepExecution(Step step) {
    step.moveOutputResultsToStep(props)
    props.pop()
  }
}

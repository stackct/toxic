package toxic.dsl

import log.Log
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
  private final static Log slog = Log.getLogger(this)
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

  static def getLog(props) {
    return props.log ?: slog
  }

  static run(TaskMaster tm, ConfigObject props, List<TaskResult> results) {
    try {
      runSerial(props.setupTestCases, results, tm, props)
      runParallel(props.testCases, results, tm, props)
      runSerial(props.teardownTestCases, results, tm, props)
    }
    catch(AbortExecutionException e) {
      TestCaseRunner runner = e.testCaseRunner
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

      getLog(props).error(Log.collectMap("Task failed", values), logStackTrace ? runner.error : null)
    }
  }

  static void runSerial(def testCases, List<TaskResult> results, TaskMaster tm, ConfigObject props) {
    if(!testCases) {
      return
    }

    TestCaseRunner runner = null
    testCases.find {
      runner = new TestCaseRunner(tm, it, props.clone())
      appendResults(results, runner.call().results)
      return runner.error
    }
    if(runner?.error) {
      throw new AbortExecutionException(runner)
    }
  }

  static void runParallel(def testCases, List<TaskResult> results, TaskMaster tm, ConfigObject props) {
    if(!testCases) {
      return
    }

    int maxAttempts = Integer.parseInt(props['pickle.testCaseAttempts'])
    def testAttempts = [:]

    def futures = []
    def pool = Executors.newFixedThreadPool(Integer.parseInt(props['pickle.testCaseThreads']))
    testCases.each {
      testAttempts[it] = 1
      futures << pool.submit(new TestCaseRunner(tm, it, props.clone()))
    }

    def finishedFutures = []
    TestCaseRunner runner = null
    while (!TaskResult.shouldAbort(props, results) && finishedFutures.size() < futures.size()) {
      futures.find {
        if (!finishedFutures.contains(it)) {
          try {
            runner = it.get(20, TimeUnit.MILLISECONDS)
            if (!TaskResult.areAllSuccessful(runner.results) && testAttempts[runner.testCase] < maxAttempts) {
              testAttempts[runner.testCase]++
              getLog(props).warn("Test case failed, will retry; test=" + runner.testCase + "; attempt=" + testAttempts[runner.testCase] + "; maxAttempts=" + maxAttempts)
              futures << pool.submit(new TestCaseRunner(tm, runner.testCase, props.clone()))
            } else {
              appendResults(results, runner.results)
            }
            finishedFutures << it
          }
          catch(TimeoutException to) { }
        }
      }
    }

    pool.shutdownNow()
    try {
      getLog(props).info("Waiting for in flight test cases to complete")
      pool.awaitTermination(60, TimeUnit.SECONDS);
    }
    catch (InterruptedException e) {
      getLog(props).error("Failed to properly shutdown test case pool", e)
    }

    if(runner?.error) {
      throw new AbortExecutionException(runner)
    }
  }

  static void appendResults(List<TaskResult> results, List<TaskResult> added) {
    synchronized (results) {
      results.addAll(added)
    }
  }

  static void appendResults(List<TaskResult> results, TaskResult added) {
    synchronized (results) {
      results.add(added)
    }
  }

  @Override
  TestCaseRunner call() {
    if (!taskMaster.shouldAbort()) {
      getLog(props).info("Starting test case; test=${props.testCase.name}")

      def success = false
      long start = 0
      long end = 0

      try {
        start = System.currentTimeMillis()
        testCase.stepSequence.eachWithIndex { seq, stepIndex ->
            props.stepIndex = stepIndex
            step = seq.step
            executeStep(step)
        }
        executeAssertions()
        end = System.currentTimeMillis()
        success = TaskResult.areAllSuccessful(this.results)
      }
      catch(Exception e) {
        error = e
      }
      finally {
        if (!taskMaster.shouldAbort()) {
          appendResults(this.results, new TaskResult([
                  id:        UUID.randomUUID().toString(),
                  family:    testCase.file.name,
                  name:      testCase.name,
                  type:      TestCaseTask.class.name,
                  success:   success,
                  error:     error?.message,
                  startTime: start,
                  stopTime:  end,
                  complete:  true,
                  duration:  (end - start)
          ]))

          getLog(props).info("Finished test case; test=${testCase.name}; file=${testCase.file.name}; success=${success}")
        } else {
          getLog(props).info("Aborting test case; test=${testCase.name}; file=${testCase.file.name}")
        }
      }
    }

    return this
  }

  void executeStep(Step step) {
    if (taskMaster.shouldAbort()) return

    preStepExecution(step)

    Function fn = props.functions[step.function]
    if (!fn?.path) return // step refers to a higher order function

    executeDirItem(new DirItem(fn.path))

    postStepExecution(step)
    if(step.lastStepInSequence)
    {
      getLog(props).info("Completing step sequence; test=${props.testCase.name}; step=${step.parentStep.name}; fn=${step.parentStep.function}")
      postStepExecution(step.parentStep)
    }
  }

  void executeAssertions() {
    if (taskMaster.shouldAbort()) return

    getLog(props).info("Executing assertions; test=${props.testCase.name}")
    def resolver = { contents ->
      def interpolatedContents = ''<<''
      contents.eachLine { interpolatedContents << Step.interpolate(props, it) + '\n' }
      interpolatedContents.toString()
    }

    executeDirItem(new DirItem(testCase.assertionFile(new File("noop"), resolver)))
  }

  void preStepExecution(Step step) {
    props.push()
    getLog(props).info("Executing step; test=${props.testCase.name}; step=${step.name}; fn=${step.function}")
    step.copyArgsToMemory(props)
  }

  void executeDirItem(DirItem dirItem) {
    DirOrganizer organizer = new DirOrganizer(props: props, dirItem: dirItem)

    while (organizer.hasNext() && !taskMaster.isShutdown() && !taskMaster.shouldAbort()) {
      Task task = organizer.next()
      def taskResults = task.execute(props)

      def t = TaskResult.getFailedTask(taskResults)
      if (t) {
        throw new AbortExecutionException(this, t.error ?: t.name + " failed")
      }

      props.taskId++
    }
  }

  void postStepExecution(Step step) {
    step.moveOutputResultsToStep(props)
    props.pop()
  }
}

class AbortExecutionException extends Exception {
  TestCaseRunner testCaseRunner

  AbortExecutionException(TestCaseRunner testCaseRunner) {
    this.testCaseRunner = testCaseRunner
  }

  AbortExecutionException(TestCaseRunner testCaseRunner, String message) {
    super(message)
    this.testCaseRunner = testCaseRunner
  }
}

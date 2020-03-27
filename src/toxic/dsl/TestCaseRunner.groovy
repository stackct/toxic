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
  private ConfigObject props
  private List<TaskResult> results
  private Exception error

  private TestCaseRunner(TaskMaster taskMaster, TestCase testCase, ConfigObject props) {
    this.taskMaster = taskMaster
    this.props = props
    this.props.testCase = testCase
    this.props.step = new StepOutputResolver(testCase, new Step(name: 'assertions'), testCase.steps)
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
      def values = [:]
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

      def tree = '';
      if(props.currentStep) {
        tree = "\n" + Step.getCurrentTree(runner.props);
      }

      getLog(props).error(Log.collectMap("Task failed", values) + tree, logStackTrace ? runner.error : null)
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
            if (!TaskResult.areAllSuccessful(runner.results) && testAttempts[runner.props.testCase] < maxAttempts) {
              testAttempts[runner.props.testCase]++
              getLog(props).warn("Test case failed, will retry; test=" + runner.props.testCase + "; attempt=" + testAttempts[runner.props.testCase] + "; maxAttempts=" + maxAttempts)
              futures << pool.submit(new TestCaseRunner(tm, runner.props.testCase, props.clone()))
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
        Step.eachStep(props.testCase.steps, props, { step ->
          if (taskMaster.shouldAbort()) return
          executeDirItem(new DirItem(props.functions[step.function].path))
        })
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
                  family:    props.testCase.file.name,
                  name:      props.testCase.name,
                  type:      TestCaseTask.class.name,
                  success:   success,
                  error:     error?.message,
                  startTime: start,
                  stopTime:  end,
                  complete:  true,
                  duration:  (end - start)
          ]))

          getLog(props).info("Finished test case; test=${props.testCase.name}; file=${props.testCase.file.name}; success=${success}")
        } else {
          getLog(props).info("Aborting test case; test=${props.testCase.name}; file=${props.testCase.file.name}")
        }
      }
    }

    return this
  }

  void executeAssertions() {
    if (taskMaster.shouldAbort()) return

    getLog(props).info("Executing assertions; test=${props.testCase.name}")
    def resolver = { contents ->
      def interpolatedContents = ''<<''
      contents.eachLine { interpolatedContents << Step.interpolate(props, it) + '\n' }
      interpolatedContents.toString()
    }

    executeDirItem(new DirItem(props.testCase.assertionFile(new File("noop"), resolver)))
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

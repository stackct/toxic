package util

import log.Log
import groovy.time.TimeCategory

class Wait {
  final static public Log log = Log.getLogger(Wait)

  static mock(options = [:], closure) {
    def sleepAction = options.sleepAction ?: { time ->}

    try {
      Thread.metaClass.'static'.sleep = { long time -> 
        sleepAction(time)
      }

      closure()
    }
    finally {
      Thread.metaClass = null
    }
  }

  public static Wait on(Closure condition) {
    new Wait(codeBlock:condition)
  }

  public static Wait indefinitely() {
    new Wait(codeBlock:{ -> false})
  }

  static pause(long timeMs) {
    Thread.sleep(timeMs)
  }

  def codeBlock
  def successCondition
  def timeout
  def interval
  def interrupt
  def start
  def maxAttempts
  def attemptCount
  def beforeRetry
  def results
  def lastResult

  private Wait() {
    this.interval = 1000
    this.successCondition = defaultSuccessCondition
    this.results = []
    this.attemptCount = 0
  }

  public Wait until(int timeout) {
    atMostMs(timeout)
  }

  public Wait atMostMs(int timeoutMs) {
    this.timeout = timeoutMs
    this
  }

  public Wait atMostAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts
    this
  }

  public Wait every(int interval) {
    this.interval = interval
    this
  }

  public Wait unless(Closure interrupt) {
    this.interrupt = interrupt
    this
  }

  public Wait beforeRetry(Closure beforeRetry) {
    this.beforeRetry = beforeRetry
    this
  }

  public Wait withSuccessCondition(Closure successCondition) {
    forCondition(successCondition)
  }

  public Wait forCondition(Closure successCondition) {
    this.successCondition = successCondition
    this
  }

  public boolean start() throws TimeoutException {
    start = DateTime.now()
    while (!successCondition(executeAttempt())) {
      if(maxAttempts) log.info("Attempt ${attemptCount} of ${maxAttempts} failed")
      if (isTimedOut())
        throw new TimeoutException()

      if (interrupt && interrupt.call())
        return false

      if(attemptsExhausted())
        throw new AttemptsExhaustedException(attemptCount)

      pause(interval)

      if(beforeRetry) {
        beforeRetry.call(attemptCount)
      }
    }
    true
  }

  // Default success condition, just returns the closure result
  private defaultSuccessCondition = { result -> result }

  private executeAttempt() {
    attemptCount++
    def result = codeBlock.maximumNumberOfParameters > 0 ? codeBlock.call(attemptCount) : codeBlock.call()
    results << result
    lastResult = result
    return result
  }

  private boolean isTimedOut() {
    if (!timeout) return false

    use (TimeCategory) {
      (DateTime.now() - start).toMilliseconds() >= this.timeout
    }
  }

  private boolean attemptsExhausted() {
    if (!maxAttempts) return false
    return attemptCount >= maxAttempts
  }

}

package util

import log.*

/**
 * Retry any operation with max retries and delay control.
 *
 *   Retry.factory(FileNotFoundException, maxTries: 2) { new File("x").open() }
 *   Retry.factory(FileNotFoundException, periodMs: 50) { new File("x").open() }
 *
 *   Retry.factory([FileNotFoundException,IoException], periodMs: 50) { new File("x").open() }
 */
class Retry {
  final static public Log log = Log.getLogger(Retry)

  static factory(retryWhenThrown, options = [:]) {
    if (!(retryWhenThrown instanceof List)) retryWhenThrown = [retryWhenThrown]

    new Retry(retryWhenThrown, options)
  }

  def name
  def retryWhenThrown
  def periodMs
  def maxTries

  private Retry(retryWhenThrown, options = [:]) {
    if (retryWhenThrown.contains(Exception)) throw new IllegalArgumentException("do not use Exception. Instead be intentional about the exceptions you manage.")

    this.retryWhenThrown = retryWhenThrown

    this.name     = options.name ?: "operation"
    this.periodMs = options.periodMs ?: 1000
    this.maxTries = options.maxTries ?: 1
  }

  def run(closure) {
    if (!(closure instanceof Closure)) throw new IllegalArgumentException("must provide a minimum of a closure to run")

    def result

    def tries = maxTries

    def finished = false
    while (!finished && tries > 0) {
      tries--
      try {
        result = closure()

        finished = true
      }
      catch (Exception e) {
        if (!retryWhenThrown.contains(e.class)) throw e

        log.debug("Retry name=${name}, maxTries=${maxTries - tries}")

        Wait.pause(periodMs)
      }
    }

    result
  }
}


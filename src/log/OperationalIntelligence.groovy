package log

import util.*

// Used as a shorter alias
class OI extends OperationalIntelligence { }

/**
 * Collect operational intelligence for a sequence of steps in an application related by a summary name.
 * Example:
 *    These steps result in a single log message of a set of key values that include the start stop and elapsed time of the 
 *    task, but also each of the pieces of information added during the run.
 *
 *    OperationalIntelligence.start("merchantReport")
 *    OperationalIntelligence.log("merchantReport", "outputFile", "merchants.csv")
 *    OperationalIntelligence.logCount("merchantReport", "numberMerchants")
 *    OperationalIntelligence.stop("merchantReport")
 *
 * Optionally, use the OI reference as an alias to the OperationalIntelligence class name.
 */
class OperationalIntelligence {
  def static log = Log.getLogger(OperationalIntelligence)

  // Map of instances of Intel by summaryName name
  static intelligence = [:]

  static {
    reset()
  }

  static start(summaryName, intelLogger = null) {
    lock(summaryName) { intel -> 
      intel.start(intelLogger)
    }
  }

  static log(summaryName, ... args) {
    lock(summaryName) { intel ->
      intel.log(args)
    }
  }

  static logCounter(summaryName, counterName) {
    lock(summaryName) { intel ->
      intel.logCounter(counterName)
    }
  }

  static stop(summaryName) {
    def text
    lock(summaryName) { intel ->
      text = intel.stop()
    }
    reset(summaryName)
    text
  }

  /*
   * Lock access to intel data using a short global lock and an intel specific granular lock.
   * Most intel will be collected on a single thread so this will minimize contention.
   */
  private static lock(summaryName, closure) {
    def intelData
    synchronized(intelligence) {
      intelData = intelligence[summaryName]
      if (!intelData) intelData = intelligence[summaryName] = new Intel(summaryName)
    }

    synchronized(intelData) {
      closure(intelData)
    }
  }

  static reset(summaryName = null) {
    synchronized(intelligence) {
      if (summaryName) intelligence[summaryName] = null
      else intelligence.clear()
    }
  }

  static boolean isCapturing(summaryName) {
    synchronized(intelligence) {
      intelligence["summaryName"]
    }
  }
}

class Intel {

  def summaryName
  def intelData

  def Intel(summaryName) {
    this.summaryName = summaryName
    this.intelData = [:]
  }

  def start(intelLogger = null) { 
    if (!intelLogger) intelLogger = Log.getLogger(OperationalIntelligence)

    if (!intelData["start"]) {
      intelData["start"] = DateTime.now()

      intelData["intelLogger"] = intelLogger
      intelData["counters"] = [:]

      intelData["entries"] = []
      intelData["entries"] << ["summary": summaryName]
    }
  } 

  def log(args) {
    if (intelData.isEmpty()) throw new IllegalArgumentException("Must call 'start(\"${summaryName}\")' before your can log anything")

    intelData["entries"] << args
  }

  def logCounter(counterName) {
    if (intelData.isEmpty()) throw new IllegalArgumentException("Must call 'start(\"${summaryName}\")' before your can log anything")

    def counter = intelData["counters"][counterName]
    if (counter == null) counter = intelData["counters"][counterName] = 0
      
    counter++
    intelData["counters"][counterName] = counter
  }

  def stop() {
    if (intelData.isEmpty()) throw new IllegalArgumentException("Must call 'start(\"${summaryName}\")' before your can log anything")

    recordStop()
    recordCounters()

    def intelLogger = intelData["intelLogger"]
    def entries = intelData["entries"]
    def text = formatEntries(entries)

    intelLogger.info(text)

    text
  }

  // Private 

  private recordStop() {
    intelData["end"] = DateTime.now()

    def entries = intelData["entries"]
    entries << ["start": intelData["start"]]
    entries << ["end": intelData["end"]]

    def elapsedSeconds = ((intelData["end"].time - intelData["start"].time) / 1000).toLong()
    entries << ["elapsedHHMMSS": DateTime.formatHHMMSS(elapsedSeconds)]
  }

  private recordCounters() {
    def entries = intelData["entries"]
    entries << intelData["counters"]
  }

  // Produce string containing all formatted entries.
  private formatEntries(entries) {
    def entryList = []
    if (entries instanceof List) {
      entryList += formatList(entries)
    }
    else if (entries instanceof HashMap) {
      entryList += formatHash(entries)
    }
    entryList.join(", ")
  }

  // Produce list of formatted strings
  private formatList(list) {
    def entryList = []

    def foundKey = false
    list.each { entry ->
      if (entry instanceof List || entry instanceof Object[]) {
        entryList += formatList(entry)
      }
      else if (entry instanceof HashMap) {
        entryList += formatHash(entry)
      }
      else {
        if (foundKey == false) {
          foundKey = entry
        }
        else {
          entryList += formatHash([(foundKey): entry])
          foundKey = false
        }
      }
    }
    entryList
  }

  // Produce list of formatted strings
  private formatHash(hash) {
    hash.collect { key, value ->
      if (value instanceof Date) {
        value = DateTime.format(value)
      }
      "${key}=\"${value}\""
    }
  }
}



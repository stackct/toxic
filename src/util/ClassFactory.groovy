package util

import log.Log

class ClassFactory {
  def static log = Log.getLogger(ClassFactory)

  static final FULL = "full"   // A full stack trace
  static final LINE = "line"   // A simple log line
  static final NONE = "none"   // No loggin, relying on caller to decide.

  static logType

  static{
    reset()
  }

  static reset() {
    logFull()
  }

  static logFull() { logType = FULL }
  static logLine() { logType = LINE }
  static logNone() { logType = NONE }

  static factory(prefix, suffix) {
    factory(prefix, suffix, (Object[])null)
  }

  static factory(prefix, suffix, List args) {
    factory(prefix, suffix, args as Object[])
  }

  static factory(prefix, suffix, Object[] args) {
    def className = "${prefix}${suffix.capitalize()}"

    try {
      def clazz = Class.forName(className)

      if (args == null) clazz.newInstance()
      else              clazz.newInstance(args)
    }
    catch (ClassNotFoundException e) {
      if (logType == FULL) log.error("Dynamic class construction failed: ${className}", e)
      else if (logType == LINE) log.error("Dynamic class construction failed: ${className}")

      throw e
    }
  }
}



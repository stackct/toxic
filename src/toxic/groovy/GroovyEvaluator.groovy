
package toxic.groovy

import toxic.ToxicProperties
import org.apache.log4j.Logger
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.runtime.InvokerHelper
import groovy.transform.Synchronized

public class GroovyEvaluator {
  protected static Logger slog = Logger.getLogger(GroovyEvaluator.class.name)

  public static Object eval(def input) {
    return eval(input, null)
  }

  protected static def convertInput(def input) {
    def allowedInput
    if (input instanceof File) {
      allowedInput = input.text
    } else if (!(input instanceof GroovyCodeSource)
    && !(input instanceof InputStream)
    && !(input instanceof Reader)
    && !ToxicProperties.isString(input)) {
      allowedInput = input.toString()
    } else {
      allowedInput = input
    }
    return allowedInput
  }

  /**
   * Evaluates/executes the given Groovy script and returns the optional result.
   *
   * @param memory optional object to pass into the script evaluation routine
   *  and bind to the "memory" variable
   * @param scriptFileName The file name to use for the script
   */
  public static Object eval(def input, Map memory, scriptFileName=null) {
    def log = memory?.log ?: slog

    if (input instanceof String && input?.startsWith("%") && input?.endsWith("%")) {
      def key = input[1..-2]
      return memory[key]
    }

    def result

    def scriptBase
    if (memory && (!memory.recompileScripts || (memory.recompileScripts?.toLowerCase() == 'false'))) {
      def key = input.hashCode() + "_" + input.size()
      if (memory.fastClassMap == null) {
        synchronized(memory) {
          if (memory.fastClassMap == null) {
            memory.fastClassMap = [:]
          }
        }
      }
      def scriptClass = memory.fastClassMap[key]
      if (!scriptClass) {
        synchronized(memory.fastClassMap) {
          scriptClass = memory.fastClassMap[key]
          if (!scriptClass) {
            if (log.isDebugEnabled()) log.debug("Caching new script class; key=${key}; inputScript=$input")
            def compiler = new CompilerConfiguration()
            scriptBase = memory?.groovyScriptBase ?: "toxic.groovy.GroovyScriptBase"
            compiler.setScriptBaseClass(scriptBase)
            def classloader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, compiler)
            if(scriptFileName) {
              scriptClass = classloader.parseClass(convertInput(input), scriptFileName)
            } else {
              scriptClass = classloader.parseClass(convertInput(input))
            }
            memory.fastClassMap[key] = scriptClass
          }
        }
      }

      def binding = new Binding()
      binding.setVariable("memory", memory)
      binding.setVariable("log", log)
      binding.setVariable("input", input)
      def sc = InvokerHelper.createScript(scriptClass, binding)
      if (log.isDebugEnabled()) log.debug("Executing script class; key=${key}; inputScript=$input")
      result = sc.run()
    } else { // "recompileScripts" enabled
      def shell
      if (memory) {
        shell = memory["groovyshell${memory?.tmId}"]
      }

      if (!shell) {
        synchronized(this) {
          if (memory) {
            shell = memory["groovyshell${memory?.tmId}"]
          }
          if (!shell) {
            def compiler = new CompilerConfiguration()

            scriptBase = memory?.groovyScriptBase
            if (!scriptBase) {
              scriptBase = "toxic.groovy.GroovyScriptBase"
            }
            compiler.setScriptBaseClass(scriptBase)

            shell = new GroovyShell(new GroovyClassLoader(), new Binding(), compiler)

            if (memory) {
              memory["groovyshell${memory.tmId}"] = shell
            }
          }
        }
      }

      shell.setVariable("memory", memory)
      shell.setVariable("log", log)
      shell.setVariable("input", input)

      def scr = convertInput(input)
      if (log.isDebugEnabled()) log.debug("Evaluating Groovy script; inputScript=$input")
      try {
        long start = System.currentTimeMillis()
        if(scriptFileName) {
          result = shell.evaluate(scr, scriptFileName)
        } else {
          result = shell.evaluate(scr)
        }
        long elapsed = System.currentTimeMillis() - start
        if (log.isDebugEnabled()) log.debug("Finished evaluating Groovy script; elapsedMs=${elapsed}; inputScript=$input")
        
        if (memory) {
          // Accumulate perf stats
          if (!memory["groovyshellAccumScriptExecMs_${memory.tmId}"]) {
            memory["groovyshellAccumScriptExecMs_${memory.tmId}"] = 0
            memory["groovyshellAccumScriptExecCount_${memory.tmId}"] = 0
          }
          memory["groovyshellAccumScriptExecMs_${memory.tmId}"] += elapsed
          memory["groovyshellAccumScriptExecCount_${memory.tmId}"] += 1
          
          // Report perf stats
          long totalMs = memory["groovyshellAccumScriptExecMs_${memory.tmId}"]
          long totalCount = memory["groovyshellAccumScriptExecCount_${memory.tmId}"]
          if (totalCount % 100 == 0) {
            log.info("Average script execution time; totalCount=${totalCount}; totalMs=${totalMs}; avgMs=${totalMs / totalCount}")
          }
        }
      } catch (Throwable t) {
        log.error("Error evaluating Groovy script; script=" + scr)
        throw t
      } finally {
        if (clearClassLoader(shell, memory)) log.debug("Cleared shell classloader cache; tmId=${memory.tmId}")
      }
    }

    return result
  }

  /**
   * Reset the class loader every X-th script instead of every time,
   * to avoid forcing the full classpath reload on each script.
   */
  public static def clearClassLoader(shell, memory) {
    def cleared = false
    
    if (shell && memory != null) {
      int maxCount = memory["groovyResetClassLoaderExecutionCount"] ? memory["groovyResetClassLoaderExecutionCount"].toInteger() : 0
      int scriptCount = memory["groovyshellExecutionsSinceLastFlush"] ? memory["groovyshellExecutionsSinceLastFlush"].toInteger() + 1 : 1
      memory["groovyshellExecutionsSinceLastFlush"] = scriptCount
      if (scriptCount >= maxCount) {
        // Reset the class loader to clean up stale classes; otherwise permgen space 
        // will become exhausted.
        shell.resetLoadedClasses()
        shell.classLoader.clearCache()
        memory["groovyshellExecutionsSinceLastFlush"] = 0
        cleared = true
      } 
    }
    return cleared
  }

  /**
   * Parses out any script contained within one more pairs of delimiter characters
   * and evaluates the script content inline.
   *
   * @param memory optional object to pass into the script evaluation routine
   *  and bind to the "memory" variable
   */
  public static def resolve(def input, Map memory, String delim = '`') {
    if ((input == null) || !ToxicProperties.isString(input)) {
      return input
    }

    // Save the raw input script in case the groovy code needs information
    // about itself.  Only do this for lazily-executed groovy scripts or
    // it will cause a stack overflow.
    if (delim == '`') {
      memory?.script = input

      // If we are only evaluating a single property, return the result of the eval (could be a non-String)
      def matcher = (input =~ /`([^`]+)`/)
      if(matcher.matches()) {
        return eval(matcher[0][1], memory)
      }
    }
    def result = ""
    def pieces = input?.split(delim)
    boolean odd = true
    pieces.each {
      if (odd) {
        result += it
      } else {
        result += eval(it, memory)
      }
      odd = !odd
    }
    return result
  }
}

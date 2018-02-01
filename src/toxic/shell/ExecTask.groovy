// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic.shell

import toxic.groovy.*
import toxic.CompareTask
import org.apache.log4j.Logger

public class ExecTask extends CompareTask {
  protected static Logger slog = Logger.getLogger(this)

  protected transmit(request, def memory) {
    def result

    if (memory.execVerbose == "true") {
      log.info("Executing command; input=${request}")
    }

    def localMem = memory.clone()

    def input = request instanceof File ? request.text : request.toString()
    if (input.contains("exec.cmd")) {
      def props = new Properties()
      props.load(new StringReader(input))
      
      localMem.putAll(props)
      
      localMem.execCmdArgs = []
      localMem.execEnvMap = [:]
      
      localMem.execCmdArgs << localMem["exec.cmd"]
      localMem.findAll { k,v -> k.startsWith("exec.arg.") }.sort { a,b -> a.key <=> b.key }.each{ k,v ->
        localMem.execCmdArgs << v
      }

      localMem.findAll { k,v -> k.startsWith("exec.env.") }.sort { a,b -> a.key <=> b.key }.each{ k,v ->
        def pieces = v.split("=")
        localMem.execEnvMap[pieces[0]] = pieces.size() > 1 ? pieces[1] : ''
      }
      input = "execWithEnv(memory.execCmdArgs,memory.execEnvMap,memory['exec.timeoutSecs']?.toLong() ?: 0)"
    } else {
      localMem.execCmd = input
      input = "exec(memory.execCmd)"
    }
    
    input += """
      memory.stdout=out
      memory.stderr=err
      """.toString()
    def exitCode = GroovyEvaluator.eval(input, localMem)

    if (memory.execVerbose == "true") {
      log.info("Execution finished; exitCode=${localMem.exitCode}; stdout=${localMem.stdout}; stderr=${localMem.stderr}")
    }
    
    result=""
    if (exitCode) result += "exitCode=${localMem.exitCode}\n"
    if (localMem.stderr) result += "stderr=${localMem.stderr}"
    if (result) result += "stdout="
    result += localMem.stdout

    return result
  }
}

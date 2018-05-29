package toxic.dsl

import toxic.TaskResult

class FunctionTask extends toxic.Task {
  List<TaskResult> doTask(def memory) {
    memory.functions = memory.functions ?: [:]
    if (input instanceof File) {
      Function.parse(input.text).each { fn ->
        String fnName = fn.name
        def dep = findDep(memory)
        if(dep) {
          fn.path = Step.interpolate(memory+[libPath:new File(dep.value, 'library').canonicalPath], fn.path)
          fnName = "${dep.key}.${fn.name}"
        }
        else {
          fn.path = Step.interpolate(memory, fn.path)
        }

        if(memory.functions.containsKey(fnName)) {
          throw new IllegalArgumentException("Found duplicated function name; name=${fnName}")
        }
        memory.functions[(fnName)] = fn
      }
    }
    return null
  }

  def findDep(def memory) {
    memory.deps.find {
      input.canonicalPath.startsWith(it.value.canonicalPath)
    }
  }
}

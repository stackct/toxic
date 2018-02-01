package toxic.dsl

import toxic.TaskResult

class FunctionTask extends toxic.Task {
  List<TaskResult> doTask(def memory) {
    memory.functions = memory.functions ?: [:]
    if (input instanceof File) {
      Function.parse(input.text).each { fn ->
        if(memory.functions.containsKey(fn.name)) {
          throw new IllegalArgumentException("Found duplicated function name; name=${fn.name}")
        }

        def dep = findDep(memory)
        if(dep) {
          fn.path = Step.interpolate(memory+[libPath:new File(dep.value, 'library').canonicalPath], fn.path)
          memory.functions["${dep.key}.${fn.name}"] = fn
        }
        else {
          fn.path = Step.interpolate(memory, fn.path)
          memory.functions[(fn.name)] = fn
        }
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

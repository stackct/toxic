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
          int existingFnScore = 0
          int newFnScore = 0
          def existingFn = memory.functions[fnName]
          if (TestCaseHandler.isTagFound(existingFn.tags, memory.includeTags)) existingFnScore++
          if (TestCaseHandler.isTagFound(existingFn.tags, memory.excludeTags)) existingFnScore--
          if (TestCaseHandler.isTagFound(fn.tags, memory.includeTags)) newFnScore++
          if (TestCaseHandler.isTagFound(fn.tags, memory.excludeTags)) newFnScore--

          if (existingFnScore == newFnScore) {
            throw new IllegalArgumentException("Found duplicated function name; name=${fnName}")
          } else if (newFnScore < existingFnScore) {
            fn = null
          }
        }
        if (fn) memory.functions[(fnName)] = fn
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

package toxic.dsl

import toxic.TaskResult

class FunctionTask extends toxic.Task {
  
  public List<TaskResult> doTask(def memory) {
    memory.functions = memory.functions ?: [:]
    
    if (! (input instanceof File) ) {
      return null
    }

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

      // Look for an existing Function with the fully-qualified name and targets
      if (memory.functions.find { n, f -> n == fnName && f.targets == fn.targets }) {
        throw new IllegalArgumentException("Found duplicated function name; name=${fnName}")
      }

      // Don't add this Function, since it doesn't target the value specified
      if (memory.target && !fn.hasTarget(memory.target) && fn.targets) {
        fn = null
      }

      if (fn) memory.functions[(fnName)] = fn
    }

    return null
  }

  def findDep(def memory) {
    memory.deps.find {
      input.canonicalPath.startsWith(it.value.canonicalPath)
    }
  }
}

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

      Function fnToAdd = null

      if (exists(fn, fnName, memory)) {
        throw new IllegalArgumentException("Found duplicated function name; function=${fn}")
      }

      if (fn.isDefault() && !memory.functions.containsKey(fnName)) {
        fnToAdd = fn
      }

      if (memory.target && fn.hasTarget(memory.target)) {
        fnToAdd = fn
      }

      if (fnToAdd) {
        memory.functions[(fnName)] = fnToAdd
      }
    }

    return null
  }

  private boolean shouldAdd(Function f, def memory) {
    f.isDefault() || (memory.target && f.hasTarget(memory.target))
  }

  private boolean exists(Function f, String qualifiedName, def memory) {
    memory.functions.find { name, fn -> name == qualifiedName && (fn == f) }
  }

  def findDep(def memory) {
    memory.deps.find {
      input.canonicalPath.startsWith(it.value.canonicalPath)
    }
  }
}

package toxic.dsl

import log.Log

class Step implements Serializable {
  static final Log slog = Log.getLogger(this)
  static final String beginVariableRegex = /\{\{/
  static final String endVariableRegex = /\}\}/
  static final String interpolationWithPaddingRegex = /\s*\{\{([^}}]+)\}\}\s*/
  static final String interpolationWithoutPaddingRegex = /\{\{([^}}]+)\}\}/
  static final String prefixDelimiter = '.'

  String name
  String function
  Map<String,Object> args = [:]
  Map<String,Object> outputs = [:]
  Wait wait
  Step parentStep
  boolean lastStepInSequence

  def getLog(props) {
    return props?.log ?: this.slog
  }

  def methodMissing(String name, args)  {
    if (this.args.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate argument '${name}' for function '${function}'")
    }
    
    // If there is only one argument, pass it along as the raw value, to not force the burden of
    // unpacking it to the consumer. If multiple (variadic) values are passed to the function, 
    // then it is reasonable for the consumer to expect the values in an array.
    this.args[name] = (args.size() == 1) ? args[0] : args
  }

  String getPrefix() {
    // If the name does not look like it has the correct format: [prefix.]name, then it doesn't have a prefix
    if (!function.contains(prefixDelimiter) || function.startsWith(prefixDelimiter) || function.endsWith(prefixDelimiter)) 
      return null

    return function.tokenize(prefixDelimiter).first()
  }

  void inheritPrefix(Step from) {
    if (!from?.prefix || this.prefix)
      return

    this.function = from.prefix + prefixDelimiter + this.function
  }

  def wait(Closure closure) {
    def w = new Wait()
    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = w
    closure()

    wait = w
  }

  Function getFunction(def props) {
    props.functions["${function}"]
  }

  void copyArgsToMemory(def props) {
    Function function = getFunction(props)
    function.validateRequiredArgsArePresent(args)
    function.args.each { arg ->
      if(arg.hasDefaultValue && !args.containsKey(arg.name)) {
        args[arg.name] = arg.defaultValue
      }
    }
    args.each { k, v ->
      function.validateArgIsDefined(k)
      def interpolatedValue = interpolate(props, v)
      getLog(props).debug("Copying step input to memory; test=${props.testCase.name}; step=${name}; fn=${function}; ${k}=${interpolatedValue}")
      props[k] = interpolatedValue
    }

    if (wait) {
      getLog(props).debug("Detected wait condition")
      props['task.retry.atMostMs'] = wait.timeoutMs
      props['task.retry.every'] = wait.intervalMs
      props['task.retry.successes'] = wait.successes
      props['task.retry.condition'] = wait.getRetryCondition(props)
    }
  }

  void moveOutputResultsToStep(props) {
    Function function = getFunction(props)
    if(function) {
      function.outputs.each { k, v ->
        def interpolatedValue = v ? interpolate(props, v) : props[k]
        getLog(props).debug("Copying step output from memory; test=${props.testCase.name}; step=${name}; fn=${function}; ${k}=${interpolatedValue}")
        outputs[k] = interpolatedValue
      }
    }
    else {
      getLog(props).debug("Skipping output result copy because function was not defined for step; step=${name}")
    }
  }

  static def interpolate(def props, String property) {
    // Attempts to match a property containing only the string value to interpolate.
    // Note that for this type of resolution, a non-String value could be returned.
    def matcher = (property =~ interpolationWithPaddingRegex)
    if(matcher.matches()) {
      return resolve(props, matcher[0][1])
    }

    // Attempts to match a property containing multiple values to interpolate with or without surrounding string values.
    // Note that for this type of resolution, a String value will always be returned.
    property.replaceAll(interpolationWithoutPaddingRegex) { all, match ->
      resolve(props, match)
    }
  }

  static def interpolate(def props, def value) {
    if ([Collection, Object[]].any { type -> type.isAssignableFrom(value.getClass()) }) {
      value = value.collect { interpolate(props, it) }
    }
    else if(value instanceof Map) {
      value.keySet().each {
        value[it] = interpolate(props, value[it])
      }
    }
    value
  }

  static def resolve(def props, def match) {
    def key = match.trim()
    def value = props

    if (key.startsWith("`") && key.endsWith("`")) {
      return props[key.replaceAll("`", "")]
    }

    key.split('\\.').each { k ->
      value = value[k]
    }

    return value
  }
}

package toxic.dsl

class Step {
  static final String beginVariableRegex = /\{\{/
  static final String endVariableRegex = /\}\}/
  static final String interpolationWithPaddingRegex = /\s*\{\{([^}}]+)\}\}\s*/
  static final String interpolationWithoutPaddingRegex = /\{\{([^}}]+)\}\}/
  static final String prefixDelimiter = '.'

  String name
  String function
  Map<String,Object> args = [:]
  Map<String,Object> outputs = [:]

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
    if(value instanceof List) {
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
    def value = props
    match.trim().split('\\.').each { key ->
      value = value[key]
    }
    value
  }
}

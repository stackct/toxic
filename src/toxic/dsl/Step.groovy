package toxic.dsl

class Step {
  static final String beginVariableRegex = /\{\{/
  static final String endVariableRegex = /\}\}/
  static final String interpolationWithPaddingRegex = /\s*\{\{([^}}]+)\}\}\s*/
  static final String interpolationWithoutPaddingRegex = /\{\{([^}}]+)\}\}/

  String name
  String function
  Map<String,Object> args = [:]
  Map<String,Object> outputs = [:]

  def methodMissing(String name, args)  {
    if (this.args.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate argument '${name}' for function '${function}'")
    }

    this.args[name] = args[0]
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

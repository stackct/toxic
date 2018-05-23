package toxic.dsl

class Variable {
  Map<String,Object> vars = [:]

  def methodMissing(String name, args)  {
    def key = name
    def val = args[0]

    if (vars.containsKey(key)) {
      throw new IllegalArgumentException("Duplicate variable '${name}'")
    }

    vars.put(key,val)
  }
}
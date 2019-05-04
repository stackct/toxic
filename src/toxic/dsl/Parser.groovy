package toxic.dsl

abstract class Parser implements Serializable {
  def results = []

  static def parse(Parser parser, String input) {
    input = parser.normalize(input)
    parse(parser, new GroovyShell().evaluate("{ -> ${input} }"))
  }

  static def parse(Parser parser, Closure closure) {
    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = parser
    closure()
    parser.results
  }

  String getKeyword() {
    return null
  }

  String normalize(String input) {
    if (keyword) {
      return input.replaceAll(/(${keyword} ".*")(\s*)(,?)(\s*\{?)/) { all, begin, optionalSpaces, optionalComma, end ->
        "${begin}, ${end.trim()}"
      }
    }
    input
  }
}

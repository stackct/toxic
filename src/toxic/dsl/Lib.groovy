package toxic.dsl

class Lib extends Parser {
  Lib() {}

  static def parse(String input) {
    parse(new Lib(), input)
  }

  def lib(String path) {
    results << path
  }
}
package toxic.dsl

class Dep extends Parser {
  String name
  String artifactId

  static def parse(String input) {
    parse(new Dep(), input)
  }

  void dep(String artifactId) {
    dep(artifactId, artifactId)
  }

  void dep(String artifactId, String name) {
    results << new Dep(name: name, artifactId: artifactId)
  }
}

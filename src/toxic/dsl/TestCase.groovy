package toxic.dsl

class TestCase extends Parser {
  String name
  String description
  Set<String> tags = [] as Set
  List<Step> steps = []
  List<String> assertions = []

  static def parse(String input) {
    parse(new TestCase(), input)
  }

  void test(String name, Closure body) {
    def test = new TestCase(name: name)
    body.setResolveStrategy(Closure.DELEGATE_FIRST)
    body.delegate = test
    body()

    results << test
  }

  def description (String description) {
    this.description = description
  }

  def tags(String... tags) {
    tags.each { tag ->
      this.tags << tag.trim()
    }
  }

  def step(String function, String name, Closure closure) {
    def t = new Step(name: name, function: function)
    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = t
    closure()
    steps << t
  }

  def assertions(Closure closure) {
    def assertion = new Assertion()

    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = assertion
    closure()

    assertions = assertion.assertions
  }

  File assertionFile(File parentFile, Closure resolver) {
    def assertionFile = ''<<''
    assertions.each { assertionFile << "${it}\n" }
    String fileName = "${name.replaceAll(' ', '_')}_assertions_${UUID.randomUUID().toString()}.groovy"
    new TransientFile(parentFile, fileName, assertionFile.toString(), resolver)
  }

  @Override
  String getKeyword() { 'test' }

  @Override
  String toString() {
    "${name} [${description}]"
  }
}

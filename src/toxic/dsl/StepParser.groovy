package toxic.dsl

class StepParser extends Parser {
  List<Step> steps = []

  def step(String function, String name, Closure closure) {
    def t = new Step(name: name, function: function)
    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = t
    closure()
    steps << t
  }
}

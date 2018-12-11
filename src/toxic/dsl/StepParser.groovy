package toxic.dsl

class StepParser extends Parser {
  List<Step> steps = []

  def step(String function, String name, Closure closure) {
    if (this.steps.find { it.name == name }) {
      throw new IllegalArgumentException("Found duplicated step name; step=${name}")
    }

    def t = new Step(name: name, function: function)
    closure.setResolveStrategy(Closure.DELEGATE_FIRST)
    closure.delegate = t
    closure()
    steps << t
  }
}

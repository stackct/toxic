package toxic.dsl

import org.junit.Test

class StepParserTest {
  @Test
  void should_parse_steps() {
    StepParser stepParser = new StepParser()
    stepParser.step('foo', 'bar') {
      input1 'value1'
    }
    assert 1 == stepParser.steps.size()
    Step step = stepParser.steps[0]
    assert 'foo' == step.function
    assert 'bar' == step.name
    assert ['input1':'value1'] == step.args
    assert [:] == step.outputs
  }
}

package toxic.dsl

import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class StepTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  @Test
  void should_collect_all_arguments() {
    def step = new Step(name:'the.step', function:'fred')

    step.arg1("foo")
    step.arg2("bar")

    assert step.args['arg1'] == 'foo'
    assert step.args['arg2'] == 'bar'
  }

  @Test
  void should_not_collect_duplicate_arguments() {
    expectedException.expect(IllegalArgumentException.class)
    expectedException.expectMessage("Duplicate argument 'arg1' for function 'fred'")

    def step = new Step(name:'the.step', function:'fred')

    step.arg1("foo")
    step.arg1("bar")

    assert step.args['arg1'] == 'foo'
  }

  @Test
  void should_interpolate_string() {
    def props = [foo:'bar', 'key with spaces':'value with spaces']
    assert 'bar' == Step.interpolate(props, '{{ foo }}')
    assert 'bar' == Step.interpolate(props, ' {{ foo }} ')
    assert 'something bar else' == Step.interpolate(props, 'something {{ foo }} else')
    assert 'value with spaces' == Step.interpolate(props, '{{ key with spaces }}')
  }

  @Test
  void should_interpolate_nested_map() {
    def props = [foo: [foo: [foo: 'bar']]]
    assert 'bar' == Step.interpolate(props, '{{ foo.foo.foo }}')
  }

  @Test
  void should_interpolate_list() {
    def props = [foo1:'bar1', foo2:'bar2', foo3:'bar3']
    assert ['bar1', 'bar2', 'bar3'] == Step.interpolate(props, ['{{ foo1 }}', '{{ foo2 }}', '{{ foo3 }}'])
  }

  @Test
  void should_interpolate_map() {
    def props = [foo1:'bar1', foo2:'bar2', foo3:'bar3']
    assert ['foo1':'bar1', 'foo2':'bar2', 'foo3':'bar3'] == Step.interpolate(props, [foo1: '{{ foo1 }}', foo2: '{{ foo2 }}', foo3: '{{ foo3 }}'])
  }
}

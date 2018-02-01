package toxic.dsl

import org.junit.Test
import org.junit.Ignore

class AssertionTest {
  @Test
  void should_evaluate_equal() {
    assert true == eq('foo', 'foo')
    assert 'foo == bar. Expression: (foo == bar)' == eq('foo', 'bar')
    assert 'foo == bar. Expression: (foo == {{bar}})' == eq('foo', '{{bar}}')
  }

  @Test
  void should_evaluate_not_equal() {
    assert true == neq('foo', 'bar')
    assert 'foo != foo. Expression: (foo != foo)' == neq('foo', 'foo')
    assert 'foo != foo. Expression: ({{foo}} != {{foo}})' == neq('{{foo}}', '{{foo}}')
  }

  @Test
  void should_evaluate_contains() {
    assert true == contains('foobar', 'oob')
    assert 'foo.contains(bar). Expression: foo.contains(bar)' == contains('foo', 'bar')
    assert 'foo.contains(bar). Expression: {{foo}}.contains({{bar}})' == contains('{{foo}}', '{{bar}}')
  }

  @Test
  void should_evaluate_startswith() {
    assert true == startswith('foobar', 'foo')
    assert 'foobar.startsWith(bar). Expression: foobar.startsWith(bar)' == startswith('foobar', 'bar')
    assert 'foobar.startsWith(bar). Expression: {{foobar}}.startsWith({{bar}})' == startswith('{{foobar}}', '{{bar}}')
  }

  @Test
  void should_evaluate_endswith() {
    assert true == endswith('foobar', 'bar')
    assert 'foobar.endsWith(foo). Expression: foobar.endsWith(foo)' == endswith('foobar', 'foo')
    assert 'foobar.endsWith(foo). Expression: {{foobar}}.endsWith({{foo}})' == endswith('{{foobar}}', '{{foo}}')
  }


  def eq(Object ying, Object yang) {
    evaluateAssertion(new Assertion().eq(ying, yang))
  }

  def neq(Object ying, Object yang) {
    evaluateAssertion(new Assertion().neq(ying, yang))
  }

  def contains(Object ying, Object yang) {
    evaluateAssertion(new Assertion().contains(ying, yang))
  }

  def startswith(Object ying, Object yang) {
    evaluateAssertion(new Assertion().startswith(ying, yang))
  }

  def endswith(Object ying, Object yang) {
    evaluateAssertion(new Assertion().endswith(ying, yang))
  }

  def evaluateAssertion(Assertion assertion) {
    assert 1 == assertion.assertions.size()
    try {
      new GroovyShell().evaluate(assertion.assertions[0])
      return true
    }
    catch(AssertionError e) {
      return e.message
    }
  }
}

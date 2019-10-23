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
  void should_evaluate_greater() {
    assert gt('b', 'a')
    assert 'a > a. Expression: (a > a)' == gt('a', 'a')
    assert 'a > b. Expression: (a > b)' == gt('a', 'b')

    assert gt('123', '122')
    assert gt('123', '122.12')
    assert gt('123.12', '122')
    assert '123 > 123. Expression: (123 > 123)' == gt('123', '123')
    assert '123 > 124. Expression: (123 > 124)' == gt('123', '124')
    assert '123 > 124.12. Expression: (123 > 124.12)' == gt('123', '124.12')

    assert gt('123.21', '122.21')
    assert '123.21 > 123.21. Expression: (123.21 > 123.21)' == gt('123.21', '123.21')
    assert '123.21 > 124.21. Expression: (123.21 > 124.21)' == gt('123.21', '124.21')
  }

  @Test
  void should_evaluate_greater_or_equal() {
    assert gte('b', 'a')
    assert gte('b', 'b')
    assert 'a >= b. Expression: (a >= b)' == gte('a', 'b')

    assert gte('123', '122')
    assert gte('123', '123')
    assert gte('123', '123.12')
    assert gte('123.12', '123')
    assert '123 >= 124. Expression: (123 >= 124)' == gte('123', '124')

    assert gte('123.21', '122.21')
    assert gte('123.21', '123.21')
    assert '123.21 >= 124.21. Expression: (123.21 >= 124.21)' == gte('123.21', '124.21')
  }

  @Test
  void should_evaluate_lesser() {
    assert lt('a', 'b')
    assert 'a < a. Expression: (a < a)' == lt('a', 'a')
    assert 'b < a. Expression: (b < a)' == lt('b', 'a')

    assert lt('121', '122')
    assert lt('121', '122.12')
    assert lt('121.12', '122')
    assert '121 < 121. Expression: (121 < 121)' == lt('121', '121')
    assert '121 < 120. Expression: (121 < 120)' == lt('121', '120')

    assert lt('121.21', '122.21')
    assert '121.21 < 121.21. Expression: (121.21 < 121.21)' == lt('121.21', '121.21')
    assert '121.21 < 120.21. Expression: (121.21 < 120.21)' == lt('121.21', '120.21')
  }

  @Test
  void should_evaluate_lesser_or_equal() {
    assert lte('a', 'b')
    assert lte('a', 'a')
    assert 'b <= a. Expression: (b <= a)' == lte('b', 'a')

    assert lte('121', '122')
    assert lte('121', '121')
    assert lte('121', '121.12')
    assert lte('121.12', '122')
    assert '121 <= 120. Expression: (121 <= 120)' == lte('121', '120')
    assert '121.12 <= 121. Expression: (121.12 <= 121)' == lte('121.12', '121')

    assert lte('121.21', '122.21')
    assert lte('121.21', '121.21')
    assert '121.21 <= 120.21. Expression: (121.21 <= 120.21)' == lte('121.21', '120.21')
  }

  @Test
  void should_evaluate_not_equal() {
    assert true == neq('foo', 'bar')
    assert 'foo != foo. Expression: (foo != foo)' == neq('foo', 'foo')
    assert 'foo != foo. Expression: ({{foo}} != {{foo}})' == neq('{{foo}}', '{{foo}}')
  }

  @Test
  void should_evaluate_string_contains() {
    assert true == contains('foobar', 'oob')
    assert 'foo.contains(bar). Expression: foo.contains(bar)' == contains('foo', 'bar')
    assert 'foo.contains(bar). Expression: {{foo}}.contains({{bar}})' == contains('{{foo}}', '{{bar}}')
  }

  @Test
  void should_evaluate_map_contains() {
    assert true == contains([foo:'bar'], 'foo')
    assert '[foo:bar].containsKey(bla). Expression: [foo:bar].containsKey(bla)' == contains([foo:'bar'], 'bla')
    assert 'foo.contains(bla). Expression: {{foo}}.contains({{bla}})' == contains('{{foo}}', '{{bla}}')
  }

  @Test
  void should_evaluate_list_contains() {
    assert true == contains(['bar','foo'], 'foo')
    assert '[foo, bar].contains(bla). Expression: [foo, bar].contains(bla)' == contains(['foo','bar'], 'bla')
    assert 'foo.contains(bla). Expression: {{foo}}.contains({{bla}})' == contains('{{foo}}', '{{bla}}')
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

  def gt(Object ying, Object yang) {
    evaluateAssertion(new Assertion().gt(ying, yang))
  }

  def gte(Object ying, Object yang) {
    evaluateAssertion(new Assertion().gte(ying, yang))
  }

  def lt(Object ying, Object yang) {
    evaluateAssertion(new Assertion().lt(ying, yang))
  }

  def lte(Object ying, Object yang) {
    evaluateAssertion(new Assertion().lte(ying, yang))
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
      new GroovyShell().evaluate(assertion.assertions.first())
      return true
    }
    catch(AssertionError e) {
      return e.message
    }
  }
}

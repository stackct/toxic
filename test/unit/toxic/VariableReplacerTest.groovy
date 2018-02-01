
package toxic

import org.junit.*

public class VariableReplacerTest {
  @Test
  public void testReplace() {
    def props = new Properties()
    props.a="There"
    def test = "Hello %a%!"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Hello There!"
  }

  @Test
  public void testReplaceMultiLine() {
    def props = new Properties()
    props.aVar1="There"
    def test = "Hello\n%aVar1%\n"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Hello\nThere\n"
  }

  @Test
  public void testReplaceMultilineInputWithVarSpanningLines() {
    def props = new ToxicProperties()
    props.aVar = "foo"
    def r = new VariableReplacer()
    r.init(props)
    // The replacer regex should not interpret "%%,bar\n%var%" as a variable named ",bar\n" that spans a line
    def text="%aVar%,%%,bar\n%aVar%,%%,bar"
    def result = r.replace(text)
    assert result == "foo,%%,bar\nfoo,%%,bar"
  }

  @Test
  public void testReplaceConsecutive() {
    def props = new Properties()
    props.aVar1="Hello"
    props.aVar2="There"
    def test = "%aVar1%%aVar2%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "HelloThere"
  }

  @Test
  public void testReplacePadRight() {
    def props = new Properties()
    props.aVar1="Pad"
    props.aVar2="Right"
    def test = "%aVar1,6, %%aVar2%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Pad   Right"
  }

  @Test
  public void testReplacePadLeft() {
    def props = new Properties()
    props.aVar1="Hello"
    props.aVar2="There"
    def test = "%aVar1%%aVar2,-6, %"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Hello There"
  }

  @Test
  public void testPadWithObject() {
    assert VariableReplacer.pad(null, "-5") == '     '
    assert VariableReplacer.pad('1', "-5") == '    1'
    assert VariableReplacer.pad('0', "-5") == '    0'
    assert VariableReplacer.pad(new Integer(0), "-5") == '    0'
    assert VariableReplacer.pad(new Integer(1), "-5") == '    1'
    assert VariableReplacer.pad(true, "-5") == ' true'
    assert VariableReplacer.pad(false, "-5") == 'false'
  }

  @Test
  public void testReplacePadLeftAssumedSpace() {
    def props = new Properties()
    props.aVar1="Hello"
    props.aVar2="There"
    def test = "%aVar1%%aVar2,-6%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Hello There"
  }

  @Test
  public void testReplaceTruncateLeft() {
    def props = new Properties()
    props.aVar1="Hello"
    props.aVar2="There"
    def test = "%aVar1%%aVar2,-3%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "Helloere"
  }

  @Test
  public void testReplaceTruncateRight() {
    def props = new Properties()
    props.aVar1="Hello"
    props.aVar2="There"
    def test = "%aVar1%%aVar2,3%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "HelloThe"
  }

  @Test
  public void testReplaceWithSum() {
    def props = new ToxicProperties()
    props["foo++"] = 1
    props["foo++"] = 2
    props["foo++"] = 3
    def test = "%+foo%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "6"
  }

  @Test
  public void testReplaceWithLastValue() {
    def props = new ToxicProperties()
    props["foo++"] = 1
    props["foo++"] = 2
    props["foo++"] = 3
    def test = "%!foo%"
    def r = new VariableReplacer()
    r.init(props)
    assert r.replace(test) == "3"
  }

  @Test
  public void testWithBacktickEvaluation() {
    def replace = { input, expected -> 
      def r = new VariableReplacer()
      def props = new ToxicProperties()
      props['foo']    = input
      r.init(props)

      def actual = r.replace('%foo%')

      assert actual == expected : "actual=${actual}; input=${input}"
    }

    // String
    replace(null, '%foo%')
    replace('', '')
    replace('bar', 'bar')
    replace('true', 'true')
    replace('false', 'false')
    
    // Boolean
    replace(false, 'false')
    replace(true, 'true')

    // List
    replace([], '[]')
    replace(['bar'], '[bar]')

    // Map
    replace([:], '[:]')
    replace(["bar":"baz"], '[bar:baz]')

    // Integer
    replace(0, '0')
    replace(1, '1')

    // Interpolation
    replace('`1-1`', '0')
    replace('`1+1`', '2')
    replace('`true || false`', 'true')
    replace('`["bar":1]["bar"]`', '1')
  }
  
  @Test
  public void testReplacerNoReplacements() {
    def props = new ToxicProperties()
    def r = new VariableReplacer()
    r.init(props)

    def expected = "%%,%%,%%,%%,%%,5.2.20.11159,2012-12-07,2,Antonio,82ee107c-7aa8-416d-b8e2-4041765785b2,2.0,0,0,1,2012-12-07T10:22:29.2423681-05:00,10,%%,%%,1,2012-12-07T10:22:29.0613681-05:00,23,3,0,%%"

    assert r.replace(expected) == expected
  }

  @Test
  public void testCase() {
    def props = new ToxicProperties()
    props.valA = "foo"
    props.valB = "bar"
    def r = new VariableReplacer()
    r.init(props)
    def input = "%valA%,%%,%valB%"
    def result = r.replace(input)
    assert result == "foo,%%,bar"
  }
}
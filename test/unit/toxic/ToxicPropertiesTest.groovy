
package toxic

import org.junit.*

public class ToxicPropertiesTest {
  @Test
  public void testGetProperty() {
    def props = new Properties()
    props.t=1
    ToxicProperties tp = new ToxicProperties(props)
    assert tp["t"] == 1
    assert tp.t == 1
    assert tp.get("t") == 1
    assert tp.getProperty("t") == 1
  }

  @Test
  public void testGetPropertyResolved() {
    def props = new Properties()
    props.t="1 + 1 = `1+1` and 2 + 2 = `2+2`"
    def expectedResult = "1 + 1 = 2 and 2 + 2 = 4"
    ToxicProperties tp = new ToxicProperties(props)
    assert tp["t"] == expectedResult
    assert tp.t == expectedResult
    assert tp.get("t") == expectedResult
    assert tp.getProperty("t") == expectedResult
  }
  
  @Test
  public void testGetIsTrue() {
    def props = new ToxicProperties()
    assert !props.isTrue("foo")
    
    [true, "true", "True", "TRUE"].each {
      props.foo = it
      assert props.isTrue("foo")
    }

    [false, "false", "False", "False", "", null].each {
      props.foo = it
      assert !props.isTrue("foo")
    }
  }

  @Test
  public void testGetPropertyRaw() {
    def props = new Properties()
    props.t="1 + 1 = `1+1` and 2 + 2 = `2+2`"
    def expectedResult = "1 + 1 = `1+1` and 2 + 2 = `2+2`"
    ToxicProperties tp = new ToxicProperties(props)
    assert tp.getRaw("t") == expectedResult
  }

  @Test
  public void testGetPropertyResolvedNested() {
    def props = new Properties()
    props.r = "15"
    props.s = 23
    props.t="1 + 1 = `1+memory.r` and 2 + 23 = `2+memory.s`"
    def expectedResult = "1 + 1 = 115 and 2 + 23 = 25"
    ToxicProperties tp = new ToxicProperties(props)
    assert tp["t"] == expectedResult
    assert tp.t == expectedResult
    assert tp.get("t") == expectedResult
    assert tp.getProperty("t") == expectedResult
  }

  @Test
  public void testResolveClass() {
    def props = new Properties()
    props.foo = "toxic.TaskMaster"
    ToxicProperties tp = new ToxicProperties(props)
    assert tp.resolveClass("foo") == Class.forName(props.foo)
  }

  @Test
  public void testResolveNullClass() {
    ToxicProperties tp = new ToxicProperties()
    assert tp.resolveClass("foo") == null
  }

  @Test
  public void testToString() {
    ToxicProperties tp = new ToxicProperties()
    tp.foo = 1
    tp.bar = "test"
    def result = tp.toString()
    assert result.contains("Last 2 modified")
    assert result.contains("[bar:test, foo:1]")
  }

  @Test
  public void testToStringShowsOnlyLastModified() {
    ToxicProperties tp = new ToxicProperties()
    for(int i=0; i<20; i++) {
      tp["foo"+i] = "test"+i
    }
    def result = tp.toString()
    assert result.contains("Last 10 modified")
    assert result.contains("[foo19:test19")
    assert result.contains("foo10:test10")
    assert !result.contains("foo0:test0")
    assert !result.contains("foo9:test9")
  }


  @Test
  public void testToStringMasksSecure() {
    ToxicProperties tp = new ToxicProperties()
    tp.foo = 1
    tp.bar = "test"
    tp['secure.baz'] = "secureBaz"
    def result = tp.toString()
    assert result.contains("[secure.baz:***, bar:test, foo:1]")

    // This fails because "${tp}" is a GString, and it's toString() method has special handling for expressions
    // that evaluate to a Map, Collection, etc. (see InvokerHelper.write(Writer, Object)). It doesn't look like
    // this behavior can be overridden, which is unfortunate.
    //def result2 = "${tp}".toString()
    //assert result2 == "[foo:1, bar:test, secure.baz:***]"
  }


  @Test
  public void testEach() {
    ToxicProperties tp = new ToxicProperties()
    tp.bar = "test"
    tp.each { key, value ->
      assert key == "bar"
      assert value == "test"
    }
  }

  @Test
  public void testPut() {
    ToxicProperties tp = new ToxicProperties()
    tp.bar = "test"
    tp.bar = null
    assert tp.bar == null
  }

  @Test
  public void testNestedMap() {
    ToxicProperties tp = new ToxicProperties()
    tp.foo.bar = 123
    assert tp.foo.bar == 123
    assert tp.foo.bar + 1 == 124
  }

  @Test
  public void testRemove() {
    ToxicProperties tp = new ToxicProperties()
    tp.bar = "test"
    assert tp.remove("bar") == "test"
    assert tp.remove("bar") == null
  }

  @Test
  public void testKeyCounter() {
    def tp = new ToxicProperties()

    tp["foo++"] = "13"
    tp["foo++"] = "34"
    tp["foo"] = "hello"

    assert tp.foo == "hello"
    assert tp.foo0 == "13"
    assert tp.foo1 == "34"
    assert tp.foo_count == 2
  }

  @Test
  public void testKeyCounterSum() {
    def tp = new ToxicProperties()

    tp["boo++"] = "1"
    tp["boo++"] = "2"
    tp["boo++"] = "3"
    tp["boo"] = "hello"
    assert tp.get("+boo") == 6
  }

  @Test
  public void testKeyCounterLastValue() {
    def tp = new ToxicProperties()

    tp["boo++"] = "1"
    tp["boo++"] = "2"
    tp["boo++"] = "3"
    tp["boo"] = "hello"
    assert tp.get("!boo") == "3"
  }

  @Test
  public void testGroovyImmediateGroovyEval() {
    def tp = new ToxicProperties()

    tp["bar"] = 4
    tp["foo"] = "``1 + memory.bar``"
    tp["bar"] = 8
    assert tp.foo == "5"

    tp["bar"] = 4
    tp["foo"] = "`1 + memory.bar`"
    tp["bar"] = 8
    assert tp.foo == 9

    tp["foo"] = "``1 + memory.bar`` + ``2 + memory.bar``"
    tp["bar"] = 4
    assert tp.foo == "9 + 10"
  }

  @Test
  public void testGroovyEmbedded() {
    def tp = new ToxicProperties()
    tp.a = "a"
    tp.bar = "hello"
    tp.foo = "`memory['b' + memory.a +'r']`"
    assert tp.foo == "hello"
  }

  @Test
  public void testLoad() {
    def tp = new ToxicProperties()
    def props = """
      # This is a test = ok?
      #
      Z=``memory.a``
      a=bar
      b=``memory.a``
      c=``memory.b``
      d=`memory.a`
      """

    tp.load(new StringReader(props))
    assert tp.Z == "[:]"
    assert tp.a == "bar"
    assert tp.b == "bar"
    assert tp.c == "bar"
    assert tp.d == "bar"
  }
  
  @Test
  public void test_forProperties() {
    def tp = new ToxicProperties()
    tp["a.0"] = 1
    tp["a.390"] = 3
    tp["a.10"] = 22
    tp["b.1"] = 4
    tp["b.10"] = 5
    tp["b.390"] = 6
    
    def found = []
    tp.forProperties("a.") { key, value -> found << value }
    
    assert found == [1,22,3]
  }
  
  @Test
  public void test_push_pop() {
    def tp = new ToxicProperties()
    tp.x = 1
    tp.y = ['hi']
    tp.push()
    assert tp.x == 1
    assert tp.y == ['hi']
    tp.x = 2
    tp.z = 'a'
    tp.y << 'bye'
    assert tp.x == 2
    assert tp.y == ['hi','bye']
    assert tp.z == 'a'
    tp.push()
    assert tp.x == 2
    assert tp.y == ['hi','bye']
    assert tp.z == 'a'
    tp.z = 'b'
    assert tp.z == 'b'
    tp.pop()
    assert tp.x == 2
    assert tp.y == ['hi','bye']
    assert tp.z == 'a'
    tp.pop()
    assert tp.x == 1
    assert tp.y == ['hi','bye']
    assert !tp.z
    tp.pop()
    assert tp.x == 1
    assert tp.y == ['hi','bye']
    assert !tp.z
  }
}

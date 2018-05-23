package toxic.dsl

import org.junit.*
import static org.junit.Assert.fail

class VariableTest {
  
  Variable var = new Variable()

  @Test
  public void should_extract_key_value_from_closure() {
    var.string("foo")
    var.bool(true)
    var.int(5)
    var.list(['foo','bar'])
    var.map([foo:'bar'])

    assert var.vars == [
      string: 'foo',
      bool: true,
      int: 5,
      list: ['foo','bar'],
      map: [foo:'bar']
    ]
  }

  @Test
  public void should_ignore_all_but_first_arg() {
    var.foo("one", "two", "three")
    
    assert var.vars == [foo:"one"]
  }

  @Test
  public void should_disallow_duplicate_vars() {
    try {
      var.foo("one")
      var.foo("two")
      
      fail("Expected exception, but didn't get one")
    }
    catch (IllegalArgumentException ex) { 
      assert ex.message == "Duplicate variable 'foo'"
    }
    
    assert var.vars == [foo:"one"]
  }
}
package toxic

import org.junit.*

class StringIteratorTest {

  @Test
  public void should_constuct_from_string() {
    def si = new StringIterator("foo")

    assert si.value == 'foo'
    assert si.idx == 0
    assert si.length == 3 
  }

  @Test
  public void should_skip_specific_number_of_characters() {
    def si = new StringIterator("foobar")
    assert si.remaining == 'foobar'

    assert si.skip()
    assert si.remaining == 'oobar'

    assert si.skip(1)
    assert si.remaining == 'obar'

    assert si.skip(2)
    assert si.remaining == 'ar'

    assert !si.skip(3)
    assert si.remaining == 'ar'

    assert si.skip(2)
    assert si.remaining == null

    assert !si.skip()
    assert si.remaining == null
  }

  @Test
  public void should_skip_over_string() {
    def si = new StringIterator("foobar")
    
    si.skip('foo')
    assert si.idx == 3
    assert si.remaining == 'bar'

    si.reset()
    assert !si.skip(null)
  }

  @Test
  public void should_skip_until_match() {
    def si = new StringIterator("foobarbaz")

    assert !si.skipUntil('NOTFOUND')
    assert si.remaining == null
    si.reset()

    assert si.skipUntil('bar')
    assert si.remaining == 'barbaz'

    assert !si.skipUntil('foo')
    assert si.remaining == null
  }

  @Test
  public void should_peek_ahead() {
    def si = new StringIterator("foobarbaz")

    assert si.peek(10) == null
    assert si.remaining == 'foobarbaz'

    assert si.peek() == 'f'
    assert si.peek(3) == 'foo'
    assert si.remaining == 'foobarbaz'

    si.skip('foo')
    assert si.peek(7) == null
    assert si.remaining == 'barbaz'
    assert si.peek(6) == 'barbaz'
    assert si.remaining == 'barbaz'

    si.skip(si.remaining)
    assert si.peek() == null
    assert si.remaining == null
  }

  @Test
  public void should_grab_specific_number_of_characters() {
    def si = new StringIterator("foobarbaz")

    assert si.grab() == 'f'
    assert si.remaining == 'oobarbaz'
    si.reset()

    assert si.grab(3) == 'foo'
    assert si.remaining == 'barbaz'

    assert si.grab(3) == 'bar'
    assert si.remaining == 'baz'

    assert si.grab(4) == null
    assert si.remaining == 'baz'

    assert si.grab(3) == 'baz'
    assert si.remaining == null
  }

  @Test
  public void should_grab_until_match() {
    def si = new StringIterator("foobarbaz")

    assert si.grabUntil('bar') == 'foo'
    assert si.remaining == 'barbaz'
    si.reset()

    assert si.grabUntil('NOTEXISTS') == null
    assert si.remaining == null
    si.reset()

    assert si.grabUntil(null) == 'foobarbaz'
    assert si.remaining == null
  }

  @Test
  public void should_peak_around() {
    def si = new StringIterator("foobarbaz")
    si.skip('foo')

    assert si.peekAround(3) == 'foobar'
    assert si.peekAround(6) == 'foobarbaz'
    assert si.peekAround(20) == 'foobarbaz'
  }

  @Test
  public void should_determine_if_is_empty() {
    assert true == new StringIterator(null).isEmpty()
    assert true == new StringIterator("").isEmpty()
    assert false == new StringIterator("foo").isEmpty()
  }
}
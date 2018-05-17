package toxic

public class StringIterator {
  String value
  int startPosition
  int idx
  int length

  public StringIterator(String s, int startPosition = 0) {
    this.value = s
    this.length = s.size()
    this.startPosition = startPosition
    reset()
  }

  boolean reset() {
    this.idx = startPosition
  }

  boolean skip(int n=1) {
    if (this.idx + n > this.length) {
      return false
    }

    (this.idx += n) && true
  }

  boolean skip(String s) {
    if (s == null) 
      return false

    skip(s.size())
  }

  boolean skipUntil(String match) {
    if (match == null) {
      return skip(this.remaining)
    }

     while (this.remaining && !this.remaining.startsWith(match)) {
      skip()
    }

    return this.remaining
  }

  String peek(int n=1) {
    if (n > this.remaining?.size()) {
      return null
    }

    return this.remaining[0..n-1]
  }

  String grab(int n=1) {
    String r = peek(n)
    skip(r)
    
    return r
  }

  String grabUntil(String match) {
    int curPos = this.idx
    
    if (skipUntil(match)) {
      return this.value[curPos..this.idx-1]
    }

    return null
  }

  String getRemaining() {
    if (this.idx > this.length-1)
      return null

    return this.value[this.idx..-1]
  }

  String peekAround(int n) {
    int start = Math.max(0, this.idx - n)
    int end   = Math.min(this.length, this.idx + n)
    
    return this.value.substring(start, end)
  }  

  @Override
  String toString() {
    return "idx=${this.idx}; remaining=${this.remaining}; value=${this.value};"
  }
}

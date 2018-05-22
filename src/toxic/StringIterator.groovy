package toxic

public class StringIterator {
  String value
  int startPosition
  int idx
  int length

  public StringIterator(String s, int startPosition = 0) {
    this.value = s
    this.length = this.value ? this.value.size() : 0
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
    if (!s) {
      return false
    }

    skip(s.size())
  }

  boolean skipUntil(String match) {
    if (!match) {
      return skip(this.remaining)
    }

     while (this.remaining && !this.remaining.startsWith(match)) {
      skip()
    }

    return this.remaining
  }

  String peek(int n=1) {
    if (n > this.remaining?.size()) {
      return nullOrEmpty()
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
    
    if (skipUntil(match) && curPos != this.idx) {
      return this.value[curPos..this.idx-1]
    }

    return nullOrEmpty()
  }

  String getRemaining() {
    if (this.idx > this.length-1) {
      return nullOrEmpty()
    }
    return this.value[this.idx..-1]
  }

  String peekAround(int n) {
    int start = Math.max(0, this.idx - n)
    int end   = Math.min(this.length, this.idx + n)
    
    return this.value.substring(start, end)
  }

  boolean isEmpty() {
    return (this.length == 0)
  }

  @Override
  String toString() {
    return "idx=${this.idx}; remaining=${this.remaining}; value=${this.value};"
  }

  String nullOrEmpty() {
    value == null ? null : ''
  }
}

package toxic

class ContentMismatchException extends ValidationException {
  def expected
  def actual

  ContentMismatchException(def expected, def actual) {
    super("Content mismatch; expected=${expected}; actual=${actual}")
    this.expected = expected
    this.actual = actual
  }
}

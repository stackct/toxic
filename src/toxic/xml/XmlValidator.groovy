package toxic.xml

import toxic.HttpValidator

class XmlValidator extends HttpValidator {
  /**
   * Perform any desired 'clean-up' of actual/test text before doing comparison.
   */
  @Override
  protected prepareText(String actualOrig, String expectedOrig) {
    // Disregard extra white space at the beginning and end of the compared
    // lines, to help avoid false positives due to negligible variances.
    def actual = trimMarkup(actualOrig)
    def expected = trimMarkup(expectedOrig)

    [actual, expected]
  }

  private String trimMarkup(String s) {
    def result = ""
    s.trim().eachLine {
      result += it.trim() + "\n"
    }
    return result.replaceAll(" />", "/>")
  }
}

package toxic.xml

import toxic.HttpValidator

class XmlValidator extends HttpValidator {
  /**
   * Perform any desired 'clean-up' of actual/test text before doing comparison.
   */
  @Override
  protected prepareText(String actualOrig, String expectedOrig) {
    def (actual, expected) = super.prepareText(actualOrig, expectedOrig)
    [trimMarkup(actual), trimMarkup(expected)]
  }

  private String trimMarkup(String s) {
    s.replaceAll(" />", "/>")
  }
}

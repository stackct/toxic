// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.apache.log4j.Logger

/**
 * Compares text, with special handling for XML-bearing HTTP message contents:
 * <ul>
 *   <li>Trims whitespace from each line</li>
 *   <li>Normalizes line endings to \n<\li>
 *   <li>Removes whitespace at end of self-closing tags</li>
 *   <li>Ignores HTTP header of actual message if the expected text does not contain an HTTP header</li>
 * </ul>
 */
public class HttpValidator extends TextValidator {
  private static Logger log = Logger.getLogger(HttpValidator.class.name)

  /**
   * Perform any desired 'clean-up' of actual/test text before doing comparison.
   */
  @Override
  protected prepareText(String actualOrig, String expectedOrig) {
    // Disregard extra white space at the beginning and end of the compared
    // lines, to help avoid false positives due to negligible variances.
    def actual = normalizeWhiteSpace(actualOrig)
    def expected = normalizeWhiteSpace(expectedOrig)

    [actual, expected]
  }

  private String normalizeWhiteSpace(String s) {
    if(null == s) {
      return null
    }
    def result = ""
    s.trim().eachLine {
      result += it.trim() + "\n"
    }
    return result.trim()
  }

  /**
   * Return the initial indexes for each string
   */
  @Override
  protected startingIndexes(String actual, String expected) {
    def actualIdx = 0
    def contentBoundaries = ['\n\n', '\r\n\r\n']

    // If the expected response does not contain the HTTP result then advance
    // actualIdx pointer to start at the content and skip over the headers.
    if (!expected?.startsWith("HTTP") && actual?.startsWith("HTTP")) {
      contentBoundaries.each { boundary ->
        if (actual.contains(boundary)) {
          actualIdx = actual.indexOf(boundary) + boundary.size()
        }
      }
    }

    [actualIdx, 0]
  }
}
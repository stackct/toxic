// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.apache.log4j.Logger

public class TextValidator implements Validator {
  protected static Logger slog = Logger.getLogger(TextValidator.class.name)

  def props
  def nearChars = 10
  def delimiter = "%"


  protected Logger getLog() {
    return props?.log ?: this.slog
  }

  public void init(def props) {
    this.props = props
    if (props?.containsKey("tvNearChars")) {
      nearChars = new Integer(props.tvNearChars.toString())
    }
    delimiter = props.tvDelimiter ? props.tvDelimiter : delimiter
  }

  public void validate(def actualOrig, def expectedOrig, def memory) {
    if (actualOrig == null && expectedOrig == null) {
      return
    }

    if ((actualOrig == null && expectedOrig != null) || (actualOrig != null && expectedOrig == null)) {
      throw new ValidationException("Content mismatch; actual=${actualOrig}; expected=${expectedOrig}")
    }

    // "clean-up" text before comparison
    def prepared = prepareText(actualOrig, expectedOrig)
    def (actual, expected) = prepared

    def expectedLineNumber = 1
    def expectedSize = expected.size()
    def actualSize = actual.size()
    def (actualIdx, expectedIdx) = startingIndexes(actual, expected)

    while ((expectedIdx < expectedSize) && (actualIdx < actualSize)) {
      if('\n' == expected[expectedIdx]) {
        expectedLineNumber++
      }
      def tag = (expectedIdx + 1 < expectedSize) ? expected.substring(expectedIdx, expectedIdx + 2) : null
      if (skipValidation(tag)) {
        expectedIdx += 2

        def actualTerminatingChar
        if (expectedIdx < expectedSize) {
          actualTerminatingChar = expected[expectedIdx]
        }

        // Advance the actual index to the terminating character, or to the end of the
        // actual content which ever applies.
        while ((actualIdx < actualSize) && (actual[actualIdx] != actualTerminatingChar)) {
          actualIdx++
        }
      }
      else if (tag == "${delimiter}>") {
        // Expected syntax: %>5% where 5 is a the number of chars to skip to.
        expectedIdx += 2

        // Extract number
        def countStartIdx = expectedIdx
        while ((expectedIdx < expectedSize) && (expected[expectedIdx] != "${delimiter}")) {
          expectedIdx++
        }
        // Ensure we didn't exceed the end of the string
        if (expectedIdx >= expectedSize) {
          throw new ValidationException("Unterminated variable definition in expected response; expected=" + expectedOrig)
        }

        def count = new Integer(expected.substring(countStartIdx, expectedIdx))
        expectedIdx++

        def expectedTerminatingString
        if (expectedIdx < expectedSize) {
          expectedTerminatingString = expected.substring(expectedIdx, expectedIdx + count)
        }

        // Advance the actual index to the terminating string, or to the end of the
        // actual content which ever applies.
        def actualTerminatingString = actual.substring(actualIdx, actualIdx + count)
        while ((actualIdx + count < actualSize) && (actualTerminatingString != expectedTerminatingString)) {
          actualIdx++
          actualTerminatingString = actual.substring(actualIdx, actualIdx + count)
        }
      }
      else if (tag == "${delimiter}#") {
        expectedIdx += 2

        // Extract the count
        def countStartIdx = expectedIdx
        while ((expectedIdx < expectedSize) && (expected[expectedIdx] != "${delimiter}")) {
          expectedIdx++
        }
        // Ensure we didn't exceed the end of the string
        if (expectedIdx >= expectedSize) {
          throw new ValidationException("Unterminated variable definition in expected response; expected=" + expectedOrig)
        }

        def count = new Integer(expected.substring(countStartIdx, expectedIdx))

        // Advanced the expected index to the next real character (after the trailing delimiteriter)
        expectedIdx++
        actualIdx += count
      } else if (isVariableAssignment(tag)) {
        expectedIdx += 2

        // Extract the variable name.
        def varNameStartIdx = expectedIdx
        while ((expectedIdx < expectedSize) && (expected[expectedIdx] != "${delimiter}")) {
          expectedIdx++
        }

        // Ensure we didn't exceed the end of the string
        if (expectedIdx >= expectedSize) {
          throw new ValidationException("Unterminated variable definition in expected response; expected=" + expectedOrig)
        }

        def varName = expected.substring(varNameStartIdx, expectedIdx)

        // Extract the actual content to save into this new variable
        def contentStartIdx = actualIdx
        def actualTerminatingChar
        expectedIdx++
        if (expectedIdx < expectedSize) {
          actualTerminatingChar = expected[expectedIdx]
        }

        while ((actualIdx < actualSize) && (actual[actualIdx] != actualTerminatingChar)) {
          actualIdx++
        }

        if (varName) {
          def content = actual.substring(contentStartIdx, actualIdx)
          performVariableAssignment(varName, content, memory)

          // Since we've set a new variable, perform a variable replacement on the
          // expected text.  Note that the variable assignment must be performed before
          // any other variable replacements.
          def r = new VariableReplacer()
          r.init(props)
          r.startDelimiter = delimiter
          r.stopDelimiter = delimiter
          expected = r.replace(expected)
          expectedSize = expected.size()
        }
      } else if (expected[expectedIdx] != actual[actualIdx]) {
        def expectedNear = extractNear(expected, expectedIdx)
        def actualNear = extractNear(actual, actualIdx)
        throw new ValidationException("Character mismatch -->" +
          "\n${'-'.multiply(25)}  ACTUAL  (${actual[actualIdx]}) ${'-'.multiply(25)}\n" +
          actualNear + "\n" +
          "\n${'-'.multiply(25)} EXPECTED (${expected[expectedIdx]}) on line ${expectedLineNumber} ${'-'.multiply(25)}\n" +
          expectedNear)
      } else {
        expectedIdx++
        actualIdx++
      }
    }

    if ((expectedIdx != expectedSize) || (actualIdx < actualSize)) {
      throw new ValidationException("Content length mismatch -->" +
          "\n${'-'.multiply(25)}  ACTUAL  (count=${actualSize}) ${'-'.multiply(25)}\n" +
          actual + "\n" +
          "\n${'-'.multiply(25)} EXPECTED (count=${expectedSize}) ${'-'.multiply(25)}\n" +
          expected)
    }
  }

  /**
   * Perform any desired 'clean-up' of actual/test and expected/control text before doing comparison.
   */
  protected prepareText(String actualOrig, String expectedOrig) {
    [actualOrig, expectedOrig]
  }

  /**
   * Return the initial indexes for each string
   */
  protected startingIndexes(String actual, String expected) {
    [0, 0]
  }

  protected String extractNear(def expected, int idx) {
    def startIdx = Math.max(0, idx - nearChars)
    def endIdx = Math.min(expected.size(), idx + nearChars)
    return expected.substring(startIdx, endIdx)
  }

  String getAssignmentToken() {
    "${delimiter}="
  }

  boolean skipValidation(def value) {
    value instanceof String && value.trim() == "${delimiter}${delimiter}"
  }

  boolean isVariableAssignment(def value) {
    value instanceof String && assignmentToken == value.trim().take(assignmentToken.size())
  }

  void performVariableAssignment(String varName, def content, def memory) {
    varName = varName.replace(assignmentToken, '').replace(delimiter, '')

    String[] varNameParts = varName.split("=")
    if (varNameParts.size() > 1) {
      // Compare the second variable with the actual value.  The first value
      // will be saved to memory after this step.
      if(memory[varNameParts[1]] != content) {
        throw new ContentMismatchException(memory[varNameParts[1]], content)
      }
    }
    varName = varNameParts[0]

    if (log.isDebugEnabled()) log.debug("Saving text snippet into memory; snippet=" + content + "; variable=" + varName)
    memory[varName] = content
  }
}

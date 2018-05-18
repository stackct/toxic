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
    def expectedLineNumber = 1

    def prepared = prepareText(actualOrig, expectedOrig)
    def (actual, expected) = prepared
    def (actualIdx, expectedIdx) = startingIndexes(actual, expected)

    StringIterator exp = new StringIterator(expected, expectedIdx)
    StringIterator act = new StringIterator(actual, actualIdx)

    while (exp.remaining && (act.remaining || act.isEmpty())) {
      if('\n' == exp.peek()) {
        expectedLineNumber++
      }

      // Tags are defined as a delimiter and one of: %,#,>
      def tag = exp.peek(delimiter.size() + 1)

      if (skipValidation(tag)) {
        exp.skip(tag)
        act.skipUntil(exp.peek())
      }
      else if (isSkipMatch(tag) || isSkipAhead(tag)) {
        exp.skip(tag)

        String count = exp.grabUntil(delimiter)
        if (!count) {
          throw new ValidationException("Unterminated variable definition in expected response; expected=" + expectedOrig)
        }

        exp.skip(delimiter)

        if (isSkipMatch(tag)) {
          exp.grab(new Integer(count)).with { terminator ->
          act.skipUntil(terminator)
          act.skip(terminator)
          }
        } else {
          act.skip(new Integer(count))
        }
      } else if (isVariableAssignment(tag)) {
        exp.skip(tag)

        String varName = exp.grabUntil(delimiter)
        
        if (!varName) {
          throw new ValidationException("Unterminated variable definition in expected response; expected=" + expectedOrig)
        }

        exp.skip(delimiter)

        String content = act.grabUntil(exp.peek())
        performVariableAssignment(varName, content, memory)

        // Since we've set a new variable, perform a variable replacement on the
        // expected text.  Note that the variable assignment must be performed before
        // any other variable replacements.
        def r = new VariableReplacer()
        r.init(props)
        r.startDelimiter = delimiter
        r.stopDelimiter = delimiter
        
        // Create a new iterator with the replaced values
        exp = new StringIterator(r.replace(exp.value), exp.idx)
      } else if (exp.peek() != act.peek()) {
        def expectedNear = exp.peekAround(nearChars)
        def actualNear = act.peekAround(nearChars)
        
        throw new ValidationException("Character mismatch -->" +
          "\n${'-'.multiply(25)}  ACTUAL  (${act.peek()}) ${'-'.multiply(25)}\n" +
          actualNear + "\n" +
          "\n${'-'.multiply(25)} EXPECTED (${exp.peek()}) on line ${expectedLineNumber} ${'-'.multiply(25)}\n" +
          expectedNear)

      } else {
        exp.skip()
        act.skip()
      }
    }

    if (exp.remaining || act.remaining) {
      throw new ValidationException("Content length mismatch -->" +
          "\n${'-'.multiply(25)}  ACTUAL  (count=${act.length}) ${'-'.multiply(25)}\n" +
          actual + "\n" +
          "\n${'-'.multiply(25)} EXPECTED (count=${exp.length}) ${'-'.multiply(25)}\n" +
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

  String getAssignmentToken() {
    "${delimiter}="
  }

  String getSkipAheadToken() {
    "${delimiter}#"
  }

  String getSkipMatchToken() {
    "${delimiter}>"
  }

  boolean skipValidation(def value) {
    value instanceof String && value.trim() == "${delimiter}${delimiter}"
  }

  boolean isVariableAssignment(Object o) {
    false
  }

  boolean isVariableAssignment(String s) {
    s.take(assignmentToken.size()) == assignmentToken
  }

  boolean isSkipAhead(String s) {
    s == skipAheadToken
  }

  boolean isSkipMatch(String s) {
    s == skipMatchToken
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

    if (log.isDebugEnabled()) {
      log.debug("Saving text snippet into memory; snippet=" + content + "; variable=" + varName)
    }

    memory[varName] = content
  }
}

package toxic.dsl

class Assertion {
  static final String variableReplacement = ''

  List<String> assertions = []

  Assertion eq(Object ying, Object yang) {
    assertOperation(ying, yang, '==')
  }

  Assertion neq(Object ying, Object yang) {
    assertOperation(ying, yang, '!=')
  }

  Assertion contains(Object ying, Object yang) {
    assertFunction(ying, yang, 'contains')
  }

  Assertion startswith(Object ying, Object yang) {
    assertFunction(ying, yang, 'startsWith')
  }

  Assertion endswith(Object ying, Object yang) {
    assertFunction(ying, yang, 'endsWith')
  }

  Assertion assertFunction(Object ying, Object yang, String function) {
    String failureMessage = failureMessage("\"${ying}.${function}(${yang})\"")
    assertions << "assert ${format(ying)}.${function}(${format(yang)}) : ${failureMessage}"
    this
  }

  Assertion assertOperation(Object ying, Object yang, String operator) {
    String failureMessage = failureMessage("\"${ying} ${operator} ${yang}\"")
    assertions << "assert ${format(ying)} ${operator} ${format(yang)} : ${failureMessage}"
    this
  }

  String format(String value) { "'${value}'" }
  String format(Object value) { value }

  String failureMessage(String message) {
    message.replaceAll(Step.beginVariableRegex, variableReplacement).replaceAll(Step.endVariableRegex, variableReplacement)
  }
}

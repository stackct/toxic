package toxic.dsl

class VariableResolver extends TestCaseResolver {
  def props

  VariableResolver(def props) {
    this.props = props
  }

  @Override
  def propertyMissing(String name)  {
    currentTestCase(props.testCases, props.stepIndex).vars[name]
  }
}

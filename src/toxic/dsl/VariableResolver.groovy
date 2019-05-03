package toxic.dsl

class VariableResolver {
  def props

  VariableResolver(def props) {
    this.props = props
  }

  def propertyMissing(String name)  {
    props.testCase.vars[name]
  }
}

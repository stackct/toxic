
package toxic.groovy

import toxic.Replacer

public class GroovyReplacer implements Replacer {
  def props

  public void init(def props) {
    this.props = props
  }

  public def replace(def input) {
    return GroovyEvaluator.resolve(input, props)
  }
}
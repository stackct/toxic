
package toxic.groovy

import org.junit.*

public class GroovyReplacerTest {
  @Test
  public void testReplace() {
    def props = new Properties()
    props.a="There"
    def test = "Hello `memory.a`!"
    def r = new GroovyReplacer()
    r.init(props)
    assert r.replace(test) == "Hello There!"
  }
}
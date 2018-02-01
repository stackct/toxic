
package toxic

import org.junit.*

public class PropertiesTaskTest {
  @Test
  public void testExecuteFile() {
    def m = new ToxicProperties()
    m.foo = 1
    def f = new File('testExecute.properties')
    f.text = """
      foo=bar
      bar=1
      """
    def pt = new PropertiesTask()
    pt.init(f, null)
    pt.execute(m)
    f?.delete()

    assert m.foo == "bar"
    assert m.bar == "1"
    assert m.propertiesFile == f
  }

  @Test
  public void testExecuteString() {
    def m = new ToxicProperties()
    def s = """
      foo=bar
      bar=1
      """
    def pt = new PropertiesTask()
    pt.init(s, null)
    pt.execute(m)

    assert m.foo == "bar"
    assert m.bar == "1"
  }
}
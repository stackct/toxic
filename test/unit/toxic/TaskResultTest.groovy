
package toxic

import toxic.groovy.GroovyTask
import toxic.xml.XmlTask
import org.junit.*

public class TaskResultTest {
  @Test
  public void should_handle_success() {
    TaskResult tr = new TaskResult("1", "testdirname", "test_req.xml", XmlTask.class.name)
    tr.success = true
    assert tr.id == "1"
    assert tr.family == "testdirname"
    assert tr.name == "test_req.xml"
    assert tr.type == "toxic.xml.XmlTask"
    assert tr.success == true
  }
  
  @Test
  public void should_deserialize_from_simple() {
    def map = [
      id:        'a',
      family:    'b',
      type:      'c',
      name:      'd',
      error:     'foo',
      success:   false,
      startTime: 123,
      stopTime:  456
    ]

    def result = new TaskResult(map)
    assert result.id == 'a'
    assert result.family == 'b'
    assert result.type == 'c'
    assert result.name == 'd'
    assert result.error.toString() == 'foo'
    assert result.success == false
    assert result.startTime == 123
    assert result.stopTime == 456
  }

  @Test
  public void should_serialize_to_simple_map() {
    def result = new TaskResult([:])

    result.id        = 'result.id'
    result.family    = 'some_family/with/slashes/and/UPPERCASE'
    result.name      = '100_foo.bar-name-of-TASK'
    result.type      = 'foo.bar.type'
    result.success   = false
    result.error     = "Oops!"
    result.startTime = 1
    result.stopTime  = 2

    result.toSimple().with { r ->
      assert r.id         == 'result.id'
      assert r.family     == 'some_family/with/slashes/and/UPPERCASE'
      assert r.suite      == 'some_family--with--slashes--and--uppercase'
      assert r.name       == '100_foo.bar-name-of-TASK'
      assert r.type       == 'foo.bar.type'
      assert r.success    == false
      assert r.error      == 'Oops!'
      assert r.startTime  == 1
      assert r.stopTime   == 2
      assert r.complete   == true
      assert r.duration   == 1
    }
  }

  @Test
  public void should_produce_suite_name() {
    assert new TaskResult([family:'10_foo/bar']).suite == '10_foo--bar'
    assert new TaskResult([family:'10_Foo/Bar']).suite == '10_foo--bar'
  }

  @Test
  void should_abort() {
    def props = new ToxicProperties()
    props.tmHaltOnErrorThreshold = 2
    assert !TaskResult.shouldAbort(props, [])

    List<TaskResult> tr = []
    tr << new TaskResult("1", "testdirname", "test_req.xml", XmlTask.class.name)
    tr << new TaskResult("1", "testdirname", "test_req.xml", XmlTask.class.name)
    tr << new TaskResult("1", "testdirname", "test_req.xml", XmlTask.class.name)
    tr.each { it.success = true } 
    assert !TaskResult.shouldAbort(props, tr)

    tr[0].success = false
    assert !TaskResult.shouldAbort(props, tr)

    tr[2].success = false
    assert TaskResult.shouldAbort(props, tr)
  }
}
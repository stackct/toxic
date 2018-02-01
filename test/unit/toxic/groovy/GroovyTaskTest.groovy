
package toxic.groovy

import toxic.TaskResult
import org.junit.*
import java.nio.file.Files

public class GroovyTaskTest {
  @Test
  public void testExecute() {
    def m = [foo:1]
    def f = new File('testExecute.groovy')
    f.text = """
      memory.foo++
      memory.bar = 'hello'
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    gt.execute(m)
    f?.delete()

    assert m.foo == 2
    assert m.bar == 'hello'
  }

  @Test
  public void testExecuteSub() {
    def m = [foo:1]
    def f = new File('testExecute.groovy')
    f.text = """
      def sub = {
        memory.sub=1000
      }
      sub()
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    gt.execute(m)
    f?.delete()
    assert m.sub == 1000
  }

  @Test
  public void testExecuteClear() {
    def m = [foo:1]
    def f = new File('testExecute.groovy')
    f.text = """
      memory.clear()
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    gt.execute(m)
    f?.delete()
    assert !m.foo
  }

  @Test
  public void testReturnsTaskResult() {
    def f = Files.createTempFile("testExecute",".groovy").toFile()
    f.deleteOnExit()
    f.text = """
      new toxic.TaskResult("taskId", "fam", "name", "type")
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    def results = gt.doTask([:])
    f?.delete()
    assert results != null
    println("results = ${results}")
    assert results.size() == 1
    assert results[0] instanceof TaskResult
    assert results[0].type == "type"
  }

  @Test
  public void testIgnoresUnknownResultTypes() {
    def f = Files.createTempFile("testExecute",".groovy").toFile()
    f.deleteOnExit()
    f.text = """
      def taskResult = new toxic.TaskResult("taskId", "fam", "name", "type")
      [ "a", "b", taskResult, "c" ]
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    def results = gt.doTask([:])
    f?.delete()
    assert results != null
    println("results = ${results}")
    assert results.size() == 1
    assert results[0] instanceof TaskResult
    assert results[0].type == "type"
  }

  @Test
  public void testConvertsEmptyResultsToNull() {
    def f = Files.createTempFile("testExecute",".groovy").toFile()
    f.deleteOnExit()
    f.text = """
      def taskResult = new toxic.TaskResult("taskId", "fam", "name", "type")
      [ "a", "b", "c" ] // Not TaskResult, will be dropped
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    def results = gt.doTask([:])
    f?.delete()
    assert results == null
  }


  @Test
  public void testConvertsNonCollectionResultToNull() {
    def f = Files.createTempFile("testExecute",".groovy").toFile()
    f.deleteOnExit()
    f.text = """
      def taskResult = new toxic.TaskResult("taskId", "fam", "name", "type")
      100 // Not a collection, will be converted to null
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    def results = gt.doTask([:])
    f?.delete()
    assert results == null
  }

  @Test
  public void testMapResult() {
    def f = Files.createTempFile("testExecute",".groovy").toFile()
    f.deleteOnExit()
    f.text = """
      [key:"value", '@@@nuttyKey@@@':"value"]
      """
    def gt = new GroovyTask()
    gt.init(f, null)
    def results = gt.doTask([:])
    f?.delete()
    assert results == null
  }

  @Test void testGeneratedScriptClassName() {
    def gt = new GroovyTask()
    assert null == gt.scriptFileName([:])
    assert "This__IsATest.groovy" == gt.scriptFileName(['taskFamily': 'This', 'taskName': 'IsATest.groovy'])
    assert "This__IsATest.script" == gt.scriptFileName(['taskFamily': 'This', 'taskName': 'IsATest.script'])
    assert "IsATest.groovy" == gt.scriptFileName(['taskName': 'IsATest.groovy'])
    assert "toxic__06_foo__22_bar__01_test.groovy" == gt.scriptFileName(['taskFamily': 'toxic/06_foo/22_bar', 'taskName': '01_test.groovy'])
    assert "replaced_dashes.txt" == gt.scriptFileName(['taskName': 'replaced-dashes.txt'])
    assert "replaced_dots.txt" == gt.scriptFileName(['taskName': 'replaced.dots.txt'])
    assert "_1digitFirst.groovy" == gt.scriptFileName(['taskName': '1digitFirst.groovy'])
    assert null == gt.scriptFileName(['taskName': '#@!@%%not-a-valid-class-name.groovy'])
    assert "_02_test__test_link__01_test.groovy" == gt.scriptFileName(['taskFamily': '02_test/test.link', 'taskName': '01_test.groovy'])
  }

}
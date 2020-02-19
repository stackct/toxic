
package toxic.groovy

import org.apache.commons.lang.exception.ExceptionUtils

import static org.junit.Assert.*
import org.junit.*

import toxic.Toxic

public class GroovyEvaluatorTest {

  @Test
  public void testEval() {
    def test = "1+1"
    assert GroovyEvaluator.eval(test) == 2
  }

  @Test
  public void testEvalFile() {
    def file = new File("foo-${System.currentTimeMillis()}")
    file.text = "assert input instanceof File; return 4"
    try{
      assert GroovyEvaluator.eval(file) == 4
    } finally {
       file.delete()
    }
  }

  @Test
  public void testSanitizeMap() {
    def map = new Properties()
    GroovyEvaluator.sanitizeMap(map, "test")
    assert map.test == null

    map.test = "hello"
    GroovyEvaluator.sanitizeMap(map, "test")
    assert map.test == null

    map.test = new HashMap()
    GroovyEvaluator.sanitizeMap(map, "test")
    assert map.test != null
  }

  @Test
  public void testEvalNotRecompileScripts() {
    def test = "1+1"
    def mem = new Properties()
    mem.fastClassMap = [:]
    assert mem.fastClassMap.size() == 0
    assert GroovyEvaluator.eval(test, mem) == 2
    assert mem.fastClassMap.size() == 1
    assert mem.fastClassMap.values()[0].superclass?.name == "toxic.groovy.GroovyScriptBase"
  }

  @Test
  public void testEvalRecompileScripts() {
    def test = "1+1"
    def mem = new Properties()
    mem.fastClassMap = [:]
    mem.recompileScripts = 'true'
    assert mem.fastClassMap.size() == 0
    assert GroovyEvaluator.eval(test, mem) == 2
    assert mem.fastClassMap.size() == 0
  }

  @Test
  public void testResolveProps() {
    def props = new Properties()
    props.a=2
    def test = "1 + memory.a = `1+memory.a`"
    assert GroovyEvaluator.resolve(test, props) == "1 + memory.a = 3"
  }

  @Test
  public void testResolve() {
    def test = "1 + 1 = `1+1`"
    assert GroovyEvaluator.resolve(test, null) == "1 + 1 = 2"
  }

  @Test
  public void testResolveMulti() {
    def test = "1 + 1 = `1+1` and 2 * 2 = `2*2`"
    def result = GroovyEvaluator.resolve(test, null)
    assert result == "1 + 1 = 2 and 2 * 2 = 4"
  }

  @Test
  void testResolveNonStringValue() {
    assert 3 == GroovyEvaluator.resolve("`1 + 2`", [:])
    assert true == GroovyEvaluator.resolve("`true`", [:])
    assert [foo: 'bar'] == GroovyEvaluator.resolve("`memory.foobar`", [foobar:[foo: 'bar']])
    assert GroovyEvaluator.resolve("`new toxic.groovy.GroovyEvaluator()`", [:]) instanceof GroovyEvaluator
  }

  @Test
  void ensure_that_common_methods_are_accessible_from_the_scripts() {
    def props = new Properties()
    props.groovyScriptBase="toxic.groovy.GroovyScriptBase"
    assertEquals Toxic.genProductVersionString("Toxic"), GroovyEvaluator.eval("version()", props)
  }

  @Test
  void should_clear_classloader_cache() {
    def reset
    def cleared
    def shell = new Object() {
      def resetLoadedClasses() { reset = true }
      def getClassLoader() { return new Object() { def clearCache() { cleared = true }}}
    }
    def memory = new Properties()

    reset = cleared = false
    assert GroovyEvaluator.clearClassLoader(shell, memory)
    assert memory["groovyshellExecutionsSinceLastFlush"] == 0
    assert reset
    assert cleared

    reset = cleared = false
    memory["groovyResetClassLoaderExecutionCount"] = 2
    assert !GroovyEvaluator.clearClassLoader(shell, memory)
    assert memory["groovyshellExecutionsSinceLastFlush"] == 1
    assert !reset
    assert !cleared

    reset = cleared = false
    assert GroovyEvaluator.clearClassLoader(shell, memory)
    assert memory["groovyshellExecutionsSinceLastFlush"] == 0
    assert reset
    assert cleared
  }

  @Test
  public void should_use_script_class_name() {
    def testScript = "throw new Exception(\"test\")"

    try {
      GroovyEvaluator.eval(testScript, ['recompileScripts':"false"], "TestScript.groovy") == 2
    } catch(Exception e) {
      def st = ExceptionUtils.getStackTrace(e)
      assert st.contains("at TestScript")
    }

    try {
      GroovyEvaluator.eval(testScript, ['recompileScripts':"true"], "TestScript2.groovy") == 2
    } catch(Exception e) {
      def st = ExceptionUtils.getStackTrace(e)
      assert st.contains("at TestScript2")
    }

  }

}

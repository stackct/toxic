
package toxic.groovy

import junit.framework.AssertionFailedError
import org.codehaus.groovy.control.CompilerConfiguration
import org.junit.*

public class GroovyScriptBaseTest {
  def shell
  def compiler
  def warns = []
  def infos = []
  def debugs = []
  def errors = []
  def memory = [:]
  
  @Before
  void before() {
    compiler = new CompilerConfiguration()
    compiler.setScriptBaseClass("toxic.groovy.GroovyScriptBase")

    def binding = new Binding()
    binding.setVariable("memory", memory)
    shell = new GroovyShell(this.class.classLoader, binding, compiler)

    def logger = new Object() {
      void warn(msg) { warns << msg }
      void info(msg) { infos << msg }
      void debug(msg) { debugs << msg }
      void error(msg) { errors << msg }
    }
    shell.setVariable("log", logger)
  }

  @After
  void after() {
    // Clear the thread-local output buffers
    new GroovyScriptBase() { public def run() { }}.resetBuffers()
  }

  @Test
  public void testExec() {
    assert 0 == shell.evaluate("exec('ls')")
  }

  @Test
  public void testExecLogFile() {
    assert 0 == shell.evaluate("exec('ls', 'gen/test.log')")
    def file = new File("gen/test.log")
    assert file.exists()
    file.delete()
  }

  @Test
  public void testExecLogging() {
    assert 0 == shell.evaluate("exec('echo password=foo')")
    assert infos[0] == "Executing shell command; cmd=echo password=***"
    assert infos[1].startsWith("Process stdout; elapsedMs=")
    assert infos[1].endsWith("\npassword=foo\n")
    assert infos[2].startsWith("Shell command finished; result=0; timeoutSecs=7200; elapsedMs=")
  }

  @Test
  public void testExecWithEnvLogging() {
    assert 0 == shell.evaluate("execWithEnv(['echo','password=foo'])")
    assert infos[0] == "Executing shell command; cmd=echo password=***"
    assert infos[1].startsWith("Process stdout; elapsedMs=")
    assert infos[1].endsWith("\npassword=foo\n")
    assert infos[2].startsWith("Shell command finished; result=0; timeoutSecs=300; elapsedMs=")
    assert shell.out.contains("password=foo")
  }

  @Test
  public void testExecWithEnvLoggingSuppressed() {
    // Should not log cmd output
    assert 0 == shell.evaluate("execWithEnvNoLogging(['echo','password=foo'],[:])")
    assert infos[0] == "Executing shell command; cmd=echo password=***"
    assert infos[1].startsWith("Shell command finished; result=0; timeoutSecs=300; elapsedMs=")

    // But should still capture it
    assert (shell.out.contains("password=foo"))
  }

  @Test
  public void testExecWithEnvLoggingExtraTermsSuppressed() {
    memory['sanitize.terms'] = ['secret']

    // Should not log cmd output
    assert 0 == shell.evaluate("execWithEnvNoLogging(['echo','secret=hideme'],[:])")
    assert infos[0] == "Executing shell command; cmd=echo secret=***"
    assert infos[1].startsWith("Shell command finished; result=0; timeoutSecs=300; elapsedMs=")

    // But should still capture it
    assert (shell.out.contains("secret=hideme"))
  }

  @Test
  public void testExecFail() {
    assert 0 != shell.evaluate("exec('ls sdfsdfsdfsdfsdfsdflkj')")
  }

  @Test
  public void testExecWithEnv() {
    assert 0 == shell.evaluate("execWithEnv(['ls'], [:])")
    assert "" != shell.out
    assert 0 == shell.err.length()
  }

  @Test
  public void testExecWithWorkingDir() {
    assert 0 == shell.evaluate("execWithEnv(['pwd'], [:], 300, '/tmp')")
    assert shell.out.contains("/tmp")
    assert 0 == shell.err.length()
  }

  @Test
  public void testExecWithEnvFail() {
    assert 0 != shell.evaluate("execWithEnv(['ls', 'sdfsdfsdfsdfsdfsdflkj'], [:])")
    assert 0 == shell.out.length()
    assert "" != shell.err
  }

  @Test
  public void testFail() {
    try {
      shell.evaluate("fail(\"this is a test\")")
    } catch(Exception e) {
      assert "this is a test" == e.getMessage()
    }
  }

  @Test
  public void testOutputBuffer() {
    def gsb = new GroovyScriptBase() { public def run() { }}
    def out1 = gsb.getOutputBuffer()
    def out2 = gsb.getOutputBuffer()
    assert out1 == out2

    out1.append("hi")
    assert out2.toString() == "hi"

    def outThread
    Thread.start {
      outThread = gsb.getOutputBuffer()
      assert !outThread.toString()
      outThread.append("bye")
    }.join()

    assert out2.toString() == "hi"
    assert outThread.toString() == "bye"
  }
  
  @Test
  public void testResetBuffers() {
    def gsb = new GroovyScriptBase() { public def run() { }}
    gsb.getOutputBuffer().append("hi")
    assert gsb.getOutputBuffer().toString() == "hi"
    gsb.getErrorBuffer().append("doh")
    assert gsb.getErrorBuffer().toString() == "doh"

    gsb.resetBuffers()
    assert gsb.getOutputBuffer().toString() == ""
    assert gsb.getErrorBuffer().toString() == ""
  }

  @Test
  public void testFetchLogger() {
    def logFile
    try {
      logFile = File.createTempFile('toxic-groovyscriptbase-testfetchlogger', '.log')
      def gsb = new GroovyScriptBase() { public def run() {}}
      def logger = gsb.fetchLogger(logFile.absolutePath)
      logger.info("testing")
      assert logFile.text.contains("testing")
    }
    finally {
      logFile?.delete()
    }
  }
}
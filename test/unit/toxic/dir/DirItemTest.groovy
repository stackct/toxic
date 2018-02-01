
package toxic.dir

import toxic.ToxicProperties
import toxic.dsl.StepFile
import toxic.groovy.GroovyReplacer
import groovy.mock.interceptor.MockFor
import org.junit.*

public class DirItemTest {
  @After
  void after() {
    DirItemHandlerFactory.metaClass = null
  }

  @Test
  public void testExists() {
    assert new DirItem("/etc/passwd").exists()
  }

  @Test
  public void testNotExists() {
    assert !(new DirItem("sdfsdfe").exists())
  }

  @Test
  public void testNextFile() {
    assert new DirItem("/etc/passwd").nextFile().exists()
  }

  @Test
  public void testNextFileDir() {
    def di = new DirItem("/tmp")
    // Assumes at least two file in the lib directory
    assert !(di.nextFile().isDirectory())
    assert !(di.nextFile().isDirectory())
  }

  @Test
  public void testNextFileDir_withPushPop() {
    def props = new ToxicProperties()
    props.pushpop = true
    def di = new DirItem("/tmp")

    assert props.stackSize() == 0
    assert di.nextFile(props)
    assert props.stackSize() >= 1
    def fileDrain = di.nextFile(props)
    while (fileDrain) {
      assert props.stackSize() >= 1
      fileDrain = di.nextFile(props)
    }
    assert props.stackSize() == 0
  }

  @Test
  public void testNextFileLink_withPushPop() {
    def f = new File("./test.ln")
    def f1 = new File("./f1")
    def f2 = new File("./f2")
    f.text = "\nd1\nd2\n\n"
    f1.text = "foo"
    f2.text = "foo"

    try {
      def props = new ToxicProperties()
      props.pushpop = true
      def di = new DirItem("./test.ln")
      assert props.stackSize() == 0
      assert di.nextFile(props)
      assert props.stackSize() == 1
      assert di.nextFile(props)
      assert props.stackSize() == 1
      assert !di.nextFile(props)
      assert props.stackSize() == 0
    } finally {
      f1.delete()
      f2.delete()
      f.delete()
    }
  }

  @Test
  public void testNextFileLink_ignore_withPushPop() {
    def f = new File("./test.ln")
    def f1 = new File("./f1")
    def f2 = new File("./f2.disabled")
    def f3 = new File("./f3")
    f.text = "\nf1\nf2.disabled\nf3\n"
    f1.text = "foo1"
    f2.text = "foo2"
    f3.text = "foo3"

    try {
      def props = new ToxicProperties()
      props.pushpop = true
      def di = new DirItem("./test.ln")
      assert props.stackSize() == 0
      assert di.nextFile(props)
      assert props.stackSize() == 1
      assert di.nextFile(props)
      assert props.stackSize() == 1
      assert !di.nextFile(props)
      assert props.stackSize() == 0
    } finally {
      f1.delete()
      f2.delete()
      f3.delete()
      f.delete()
    }
  }

  @Test
  public void testNextFileLink() {
    def f = new File("./test.ln")
    def d1 = new File("./d1")
    def d2 = new File("./d2")
    def f1 = new File("./d1/f1")
    def f2 = new File("./d2/f2.if")
    def f3 = new File("./d2/f3")
    def f4 = new File("./d2/f4.disabled")
    def f5 = new File("./d2/f5.if")
    def f6 = new File("./d2/f6")
    f.text = "\nd1\nd2\n\n"
    d1.mkdir()
    d2.mkdir()
    f1.text = "foo"
    f2.text = "return true"
    f3.text = "foo"
    f4.text = "foo"
    f5.text = "return false"
    f6.text = "foo"
    def di = new DirItem(f)
    def t1 = di.nextFile()
    def t3 = di.nextFile()
    def t4 = di.nextFile()
    def t5 = di.nextFile()
    f6.delete()
    f5.delete()
    f4.delete()
    f3.delete()
    f2.delete()
    f1.delete()
    d2.delete()
    d1.delete()
    f.delete()
    assert t1.name == "f1"
    assert t3.name == "f3"
    assert t4 == null
    assert t5 == null
  }

  @Test
  public void testLastPath() {
    def props = new Properties()
    def f = new File("test.link")

    def targetFile = new File("linkTarget")
    targetFile.createNewFile()
    targetFile.text = "dummy"

    f.text = "./linkTarget"
    try {
      def di = new DirItem(f).nextFile(props)
      assert props.doLastPath == "test.link"
    } finally {
      f.delete()
      targetFile.delete()
    }
  }

  @Test
  public void testWindowsLinkPath() {
    def props = new Properties()
    def f = new File("test.link") {
      String getParent() { return "dummy" }
    }
    f.text = "c:\\test.xml"

    def targetFile = new File("c:\\test.xml")
    targetFile.createNewFile()
    targetFile.text = "dummy"

    try {
      def di = new DirItem(f).nextFile(props)
      assert di.path == "c:\\test.xml"
    } finally {
      f.delete()
      targetFile.delete()
    }
  }

  @Test
  public void testFlawedIfFile() {
    def props = new Properties()
    def dir = new File("test.dir")
    dir.mkdir()
    def f1 = new File(dir, "00_test.if")
    f1.text = "return new Integer(null)"
    def f2 = new File(dir, "01_test.groovy")
    f2.text = "print 'hi'"
    try {
      def di = new DirItem(dir).nextFile(props)
      assert !di
    } finally {
      f1?.delete()
      f2?.delete()
      dir?.delete()
    }
  }

  @Test
  public void testVariableInLinkFile() {
    def props = new ToxicProperties()
    def f = new File("test.link") {
      String getParent() { return "dummy" }
    }
    props["task.replacer.1"] = "toxic.groovy.GroovyReplacer"
    props["task.replacer.2"] = "toxic.VariableReplacer"

    def tempDir = File.createTempDir()
    def tempFile = new File(tempDir, "test.xml")
    tempFile.createNewFile()
    props.myDir = tempDir.getAbsolutePath()

    try {
      f.text = "`1+1; return '  '`\n%myDir%/test.xml"
      def di = new DirItem(f).nextFile(props)
      f.delete()
      assert di.path == tempDir.getAbsolutePath()+"/test.xml"
    } finally {
      try { tempFile.delete() } catch(Throwable e) {}
      try { tempDir.delete() } catch(Throwable e) {}
    }
  }

  @Test
  public void testRootPathInLinkFile() {
    def props = new ToxicProperties()
    def f = new File("test.link") {
      String getParent() { return "dummy" }
    }
    props["task.replacer.1"] = "toxic.groovy.GroovyReplacer"
    props["task.replacer.2"] = "toxic.VariableReplacer"

    def tempRoot = File.createTempDir()
    def tempFile = new File(tempRoot, "test.xml")
    tempFile.createNewFile()
    props.rootPath = tempRoot.getAbsolutePath()

    try {
      f.text = "^/test.xml"
      def di = new DirItem(f).nextFile(props)
      f.delete()
      assert di.path == tempRoot.getAbsolutePath()+"/test.xml"
    } finally {
      try { tempFile.delete() } catch(Throwable e) {}
      try { tempRoot.delete() } catch(Throwable e) {}
    }
  }

  def winningTest(name, result, chance = null) {
    def di = new DirItem(new File("fake"))
    if (chance != null) {
      di.random = { chance } 
    }
    assert di.winning(new File(name)) == result
  }

  @Test
  public void testWinning() {
    winningTest("/foot/mouth/000_percent", false)
    winningTest("/foot/mouth/000_percent_foo", false)
    winningTest("/foot/mouth/100_percent", true)
    winningTest("/foot/mouth/100_percent_foo", true)
    winningTest("/foot/mouth/50_percent", false, 51)
    winningTest("/foot/mouth/50_percent_foo", false, 51)
    winningTest("/foot/mouth/50_percent", false, 50)
    winningTest("/foot/mouth/50_percent_foo", false, 50)
    winningTest("/foot/mouth/50_percent", true, 49)
    winningTest("/foot/mouth/50_percent_foo", true, 49)
    winningTest("/foot/mouth/1_percent", false, 1)
    winningTest("/foot/mouth/1_percent_foo", false, 1)
    winningTest("/foot/mouth/1_percent", true, 0)
    winningTest("/foot/mouth/1_percent_foo", true, 0)
    winningTest("/foot/mouth/0_percent", false, 0)
    winningTest("/foot/mouth/0_percent_foo", false, 0)
    winningTest("/foot/mouth/99_percent", false, 99)
    winningTest("/foot/mouth/99_percent_foo", false, 99)
    winningTest("/foot/mouth/100_percent", true, 99)
    winningTest("/foot/mouth/100_percent_foo", true, 99)
  }
  
  @Test
  public void testPercentageFile() {
    def f = new File("./d")
    f.mkdir()
    
    def f1 = new File("./d/00_percent")
    f1.text = "foo"

    def f2 = new File("./d/100_percent")
    f2.text = "bar"

    def f3 = new File("./d/yes")
    f3.text = "y"

    def di = new DirItem(f)
    def r1 = di.nextFile()
    def r2 = di.nextFile()
    def r3 = di.nextFile()
    
    assert r1.text == "bar"
    assert r2.text == "y"
    assert !r3

    f1.delete()
    f2.delete()
    f3.delete()
    f.delete()
  }

  @Test
  public void testInvalidLinkFile() {
    def props = new ToxicProperties()
    def f = new File("test.link") {
      String getParent() { return "dummy" }
    }

    try {
      f.text = "/invalid_file.xml"
      def di = new DirItem(f).nextFile(props)
      assert !di.exists()
    } finally {
      f.delete()
    }
  }

  @Test
  public void testLinkToDirectory() {
    def props = new ToxicProperties()
    def f = new File("test.link") {
      String getParent() { return "dummy" }
    }
    props["task.replacer.1"] = "toxic.groovy.GroovyReplacer"
    props["task.replacer.2"] = "toxic.VariableReplacer"

    def tempDir = File.createTempDir()
    def tempFile = new File(tempDir, "test.xml")
    tempFile.createNewFile()
    props.doPathDelimiter = "/"

    try {
      f.text = tempDir.absolutePath
      def di = new DirItem(f).nextFile(props)
      f.delete()
      assert di.path == tempDir.absolutePath+"/test.xml"
    } finally {
      try { tempFile.delete() } catch(Throwable e) {}
      try { tempDir.delete() } catch(Throwable e) {}
    }
  }

  @Test
  public void testLinkMultilineExpansion() {
    def props = new ToxicProperties()
    // Link file containing an inline script that generates multiple lines
    def l = new File("./test.ln")
    def f1 = new File("f1")
    def f2 = new File("f2")
    def f3 = new File("f3")
    l.text = "`(1..2).collect { \"./f\${it}\" }.join(\"\\n\")`\nf3"
    def di = new DirItem(l)
    props['task.replacer.1']=GroovyReplacer.class.name
    def t1 = di.nextFile(props)
    def t2 = di.nextFile(props)
    def t3 = di.nextFile(props)
    f3.delete()
    f2.delete()
    f1.delete()
    l.delete()
    assert t1.name == "f1"
    assert t2.name == "f2"
    assert t3.name == "f3"
  }

  @Test
  public void testSuiteFiltering() {
    def scenarios = [
      [files:['f1','f2','f3'], expected:['f1','f3'], filter: 'f1,f3'],
      [files:['f1','f2','f3'], expected:['f1','f2','f3'], filter: null],
      [files:['f1','f2','f3', 'f4'], expected:['f1','f4'], filter: 'f1,f4'],
      [files:['f1','f2','f3', 'f4'], expected:[], filter: 'UNKNOWN'],
      [files:[], expected:[], filter: 'f1,f4'],
      [files:['f1','f2','f3'], expected:[], filter: 'f1,f3', bad:true]
    ]

    scenarios.each { scenario ->
      testSuiteFilterScenario(scenario.files, scenario.filter, scenario.expected, scenario.bad)
    }
  }

  private void testSuiteFilterScenario(filenames, filter, expected, bad=false) {
    def props = new ToxicProperties()
    props['suites'] = filter

    def l = new File("./test.suite")
    def sb = new StringBuilder()
    def files = []

    filenames.each { f -> 
      files << new File(f)
      sb.append((bad ? "" : "${f}    ") +  "/path/to/suite/${f}\n")
    }
    
    l.text = sb.toString()

    def di = new DirItem(l)

    def traversals = filenames.collect { f -> di.nextFile(props) }.findAll { it != null }

    files.reverse().each { f -> f.delete() }
    l.delete()

    assert traversals.collect { it?.name } == expected
  }

  @Test
  void should_support_test_cases() {
    assert false == new DirItem('test').isTestCase()
    assert false == new DirItem('test.testDisabled').isTestCase()
    assert false == new DirItem('test.fail').isTestCase()
    assert false == new DirItem('test.disabled').isTestCase()
    assert true == new DirItem('sample.test').isTestCase()
  }

  @Test
  void should_support_deps() {
    assert false == new DirItem('dep').isDep()
    assert false == new DirItem('dep.depDisabled').isDep()
    assert false == new DirItem('dep.fail').isDep()
    assert false == new DirItem('dep.disabled').isDep()
    assert false == new DirItem('dep.deps').isDep()
    assert true == new DirItem('sample.dep').isDep()
  }

  @Test
  void should_complete_task_file() {
    DirItemHandlerFactory.metaClass.'static'.make = { DirItem item, props ->
      [nextFile:{File f -> null}, resume: {true}]
    }

    def props = ['test1':'value1']
    def mockFile = new MockFor(StepFile)
    mockFile.demand.asBoolean { true }
    mockFile.demand.getName(3) { 'test' }
    mockFile.demand.complete { def p -> assert p == props }
    mockFile.use {
      StepFile taskFile = new StepFile('test')
      new DirItem(taskFile).nextFile(props)
    }
  }

  @Test
  void should_not_complete_task_file_when_tasks_are_not_complete() {
    DirItemHandlerFactory.metaClass.'static'.make = { DirItem item, props ->
      [nextFile:{File f -> new File('someOtherTask')}, resume: {true}]
    }

    def mockFile = new MockFor(StepFile)
    mockFile.demand.asBoolean { true }
    mockFile.demand.getName(3) { 'test' }
    mockFile.demand.complete(0) { def p -> }
    mockFile.use {
      StepFile taskFile = new StepFile('test')
      new DirItem(taskFile).nextFile(['test1':'value1'])
    }
  }
}

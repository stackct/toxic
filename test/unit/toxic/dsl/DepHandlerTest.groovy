package toxic.dsl

import toxic.ToxicProperties
import toxic.dir.DirItem
import toxic.ivy.IvyClient
import groovy.mock.interceptor.MockFor
import org.junit.After
import org.junit.Test

class DepHandlerTest {
  @After
  void afterr() {
    IvyClient.metaClass = null
  }

  @Test
  void should_construct() {
    DirItem dirItem = new DirItem('something.test')
    def props = ['arg1': 'value1', 'arg2': 'value2']
    DepHandler depHandler = new DepHandler(dirItem, props)
    assert dirItem == depHandler.item
    assert props == depHandler.props
  }

  @Test
  void should_only_initialize_once() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [deps: [artifact: artifactsDir.absolutePath]]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert 1 == dirItem.children.size()

        assert null == new DepHandler(dirItem, props).nextFile(file)
        assert 1 == dirItem.children.size()
      }
    }
  }

  @Test
  void should_resolve_dep_from_cache() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      boolean resolved = false
      IvyClient.metaClass.resolve = {
        resolved = true
      }
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [homePath:baseDir.absolutePath]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
        assert resolved
      }
    }
  }

  @Test
  void should_resolve_ivy_settings_from_props() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      boolean resolved = false
      IvyClient.metaClass.resolve = {
        resolved = true
        assert delegate.ivySettingsFile.absolutePath.startsWith('/mockbuildcommonhome')
      }
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        ToxicProperties props = new ToxicProperties()
        props.homePath = baseDir.absolutePath
        props.buildCommonPath = '/mockbuildcommonhome'

        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
        assert resolved
      }
    }
  }

  @Test
  void should_resolve_ivy_settings_from_env() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      def systemMock = new MockFor(System)
      systemMock.demand.getenv(2) { "/\${${it}}" }
      systemMock.use {
        boolean resolved = false
        IvyClient.metaClass.resolve = {
          resolved = true
          assert delegate.ivySettingsFile.absolutePath.startsWith('/${BUILD_COMMON_HOME}')
        }
        DirItem dirItem = new DirItem('something.dep')
        mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
          ToxicProperties props = new ToxicProperties()
          props.homePath = baseDir.absolutePath

          assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
          assert [artifact: artifactsDir] == props.deps
          assert resolved
        }
      }
    }
  }

  @Test
  void should_not_resolve_dep_from_cache_when_disabled() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      boolean resolved = false
      IvyClient.metaClass.resolve = {
        resolved = true
      }
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [useDepsCache: true, homePath:baseDir.absolutePath]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
        assert !resolved
      }
    }
  }

  @Test
  void should_not_add_functions_child_when_cache_dir_does_not_exist() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      def props = [useDepsCache: true, homePath:'/home']
      assert null == new DepHandler(dirItem, props).nextFile(file)
      assert 0 == dirItem.children.size()
    }
  }

  @Test
  void should_resolve_local_dep() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [deps: [artifact: artifactsDir.absolutePath]]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
      }
    }
  }

  @Test
  void should_resolve_local_dep_as_file() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [deps: [artifact: artifactsDir]]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
      }
    }
  }

  @Test
  void should_resolve_from_string_key() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = ['deps.artifact': artifactsDir.absolutePath]
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
      }
    }
  }

  @Test
  void should_not_add_functions_child_when_dir_does_not_exist() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      DirItem dirItem = new DirItem('something.dep')
      def props = [homePath:'/home', deps: [artifact: '/home/artifact']]
      assert null == new DepHandler(dirItem, props).nextFile(file)
      assert 0 == dirItem.children.size()
    }
  }

  def mockFile(String fileText, Closure c) {
    def file
    try {
      file = File.createTempFile('depHandler', '.dep')
      file.text = fileText
      c(file)
    }
    finally {
      file?.delete()
    }
  }

  def mockFnDir(Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      File artifactsDir = new File(tempDir, 'gen/toxic/deps/artifact')
      File fnDir = new File(artifactsDir, 'functions')
      fnDir.mkdirs()
      File mockFn = new File(fnDir, 'test.fn')
      mockFn.text = 'function{}'
      c(tempDir, artifactsDir, mockFn)
    }
    finally {
      tempDir?.deleteDir()
    }
  }
}

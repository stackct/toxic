package toxic.dsl

import toxic.dir.DirItem
import org.junit.After
import org.junit.Test

class DepHandlerTest {
  @After
  void afterr() {
    DepResolver.metaClass = null
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
        def props = [deps: [artifact: artifactsDir]]
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
      DepResolver.metaClass.resolve = {
        resolved = true
      }
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [homePath:baseDir.absolutePath, 'pickle.repoUrl': 'http://localhost']
        assert expectedChild == new DepHandler(dirItem, props).nextFile(file)
        assert [artifact: artifactsDir] == props.deps
        assert resolved
      }
    }
  }

  @Test
  void should_not_resolve_dep_from_cache_when_disabled() {
    String input = 'dep "artifact"'
    mockFile(input) { file ->
      boolean resolved = false
      DepResolver.metaClass.resolve = {
        resolved = true
      }
      DirItem dirItem = new DirItem('something.dep')
      mockFnDir { File baseDir, File artifactsDir, File expectedChild ->
        def props = [useDepsCache: true, homePath:baseDir.absolutePath, 'pickle.repoUrl': 'http://localhost']
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
      def props = [useDepsCache: true, homePath:'/foo', 'pickle.repoUrl': 'http://localhost']
      DepHandler.log.track { logger ->
        assert null == new DepHandler(dirItem, props).nextFile(file)
        assert 0 == dirItem.children.size()
        assert logger.isLogged('skipping non-existent or empty deps function dir; fnDir=/foo/gen/deps/artifact/functions')
      }
    }
  }

  @Test
  void should_resolve_local_dep() {
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
      def props = [homePath:'/home', deps: [artifact: new File('/foo/does/not/exist')]]
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
      File artifactsDir = new File(tempDir, 'gen/deps/artifact')
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

package toxic.dsl

import org.junit.Test

class TransientDirTest {
  @Test
  void should_construct() {
    File file = new File('/path/to/parent', 'foo')
    TransientDir dir = new TransientDir(file)
    assert file.parent == dir.absolutePath
    assert dir.exists()
    assert dir.isDirectory()
    assert 1 == dir.listFiles().size()
    assert file == dir.listFiles()[0]
  }
}

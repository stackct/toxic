package toxic.dsl

import org.junit.Test

class TransientFileTest {
  @Test
  void should_construct_transient_file_with_default_resolver() {
    File parent = new File('parent')
    File file = new TransientFile(parent, 'fileName.groovy', 'k=v')
    assert parent == file.parentFile
    assert file.exists()
    assert !file.isDirectory()
    assert 'fileName.groovy' == file.name
    assert 'k=v' == file.text
  }

  @Test
  void should_construct_transient_file_with_resolver() {
    File parent = new File('parent')
    def resolver = { contents ->
      contents.replaceAll('=', '==')
    }
    File file = new TransientFile(parent, 'fileName.groovy', 'k=v', resolver)
    assert parent == file.parentFile
    assert file.exists()
    assert !file.isDirectory()
    assert 'fileName.groovy' == file.name
    assert 'k==v' == file.text
  }
}

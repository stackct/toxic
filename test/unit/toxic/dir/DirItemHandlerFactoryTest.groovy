package toxic.dir

import toxic.dsl.DepHandler
import toxic.dsl.TestCaseHandler
import org.junit.Test

class DirItemHandlerFactoryTest {
  @Test
  void should_create_handler() {
    assert DirItemHandlerFactory.make(new DirItem('test'), [:]) instanceof DirHandler
    assert DirItemHandlerFactory.make(new DirItem('test.link'), [:]) instanceof LinkHandler
    assert DirItemHandlerFactory.make(new DirItem('test.suite'), [:]) instanceof LinkHandler
    assert DirItemHandlerFactory.make(new DirItem('test.test'), [:]) instanceof TestCaseHandler
    assert DirItemHandlerFactory.make(new DirItem('test.dep'), [:]) instanceof DepHandler
    assert DirItemHandlerFactory.make(new DirItem('test.unknown'), [:]) instanceof BaseHandler
  }
}

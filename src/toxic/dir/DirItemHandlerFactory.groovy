package toxic.dir

import toxic.dsl.DepHandler
import toxic.dsl.TestCaseHandler

public class DirItemHandlerFactory {
  public static DirItemHandler make(DirItem item, props) {

    if (item.isDirectory()) {
      return new DirHandler(item, props) 
    }

    if (item.isLink()) {
      return new LinkHandler(item, props)
    }

    if (item.isLinkedSuite()) {
      return new LinkedSuiteHandler(item, props)
    }

    if (item.isTestCase()) {
      return new TestCaseHandler(item, props)
    }

    if (item.isDep()) {
      return new DepHandler(item, props)
    }

    return new BaseHandler(item, props)
  }
}
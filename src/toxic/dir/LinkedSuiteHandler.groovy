package toxic.dir

import org.apache.log4j.Logger

public class LinkedSuiteHandler extends LinkHandler {
  private static Logger log = Logger.getLogger(LinkedSuiteHandler.class.name)

  private List<File> suites = []
  private File suitePath

  public LinkedSuiteHandler(DirItem item, props) {
    super(item, props)

    if (props['suites']) {
      this.suites = props['suites'].tokenize(',')
    }
  }

  @Override
  protected File getLinkedFile(String link, String parent) {
    super.getLinkedFile(link, null)
  }

  @Override
  protected void addChild(DirItem child) {
    def parts = child.file.path.tokenize(" ")

    if (parts.size() != 2) {
      log.warn("Could not read linked suite entry; path=${child.file.path}")
      return
    }

    def suiteName = parts[0]
    def suitePath = parts[1]

    if (suites) {
      log.debug("filtering linked suites; suites=${suites}")
    }

    if ((suites && suiteName in suites) || !suites) {
      child.file = new File(suitePath)
      super.addChild(child)
    }
  }
}
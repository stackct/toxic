package toxic.dir

import toxic.CompareTask
import org.apache.log4j.Logger

public class LinkHandler implements DirItemHandler {
  private static Logger log = Logger.getLogger(LinkHandler.class.name)

  DirItem item
  def props

  public LinkHandler(DirItem item, props) {
    this.item = item
    this.props = props
  }

  public File nextFile(File f) {
    if (!item.children) {
      if (props?.pushpop) {
        log.debug("Pushing memory stack due to link file; stackSize=${props.stackSize()}; file=${item.file}; parent=${item.parent?.file}")
        props.push()
      }
      f.eachLine {
        if (it.trim().size() > 0) {
          def linkStr = CompareTask.replace(it, props)
          log.debug("Link line contents after replacement: ${linkStr}")

          linkStr.eachLine { link ->
            if (!shouldIgnore(link)) {
              def linkedFile = getLinkedFile(link, f.parent)

              if (linkedFile.exists()) {
                log.debug("Linking in path; path=$link")
              } else {
                log.debug("Linked file does not exist: $link")
              }

              addChild(new DirItem(linkedFile, item))
            }
          }
        }
      }
    }

    return item.nextChild(props)
  }

  protected boolean shouldIgnore(String link) {
    link.trim().size() == 0 || link.trim().startsWith("#")
  }

  protected File getLinkedFile(String link, String parent) {
    if (link.startsWith("^")) {
      link = link.replaceFirst("\\^", props.rootPath)
    }

    if (!link.startsWith("/") && !link.contains(":\\") && (parent != null)) {
      link = parent + "/" + link
    }

    return new File(link)
  }

  protected void addChild(DirItem child) {
    item.children << child
  }

  public boolean resume() {
    true
  }
}


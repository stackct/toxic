package toxic.dir

import org.apache.log4j.Logger

public class DirHandler implements DirItemHandler {
  private static Logger log = Logger.getLogger(DirHandler.class.name)  

  DirItem item
  def props

  public DirHandler(DirItem item, props) {
    this.item = item
    this.props = props
  }

  public File nextFile(File f) {
    if (!item.children) {
      // new subdir so protect memory
      if (props?.pushpop) {
        log.debug("Pushing memory stack due to subdir; file=${f}; parent=${item.parent?.file}")
        props.push()
      }
      def unsorted = []
      f.eachFile {
        unsorted << new DirItem(it, item)
      }
      item.children = unsorted.sort()
    }
    
    f = item.nextChild(props)

    if (f?.name?.endsWith(".if")) {
      return new IfHandler(item, props).nextFile(f)
    }

    return f
  }

  public boolean resume() {
    true
  }
}



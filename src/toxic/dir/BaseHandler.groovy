package toxic.dir

import org.apache.log4j.Logger

public class BaseHandler implements DirItemHandler {
  private static Logger log = Logger.getLogger(BaseHandler.class.name)

  DirItem item
  def props

  public BaseHandler(DirItem item, props) {
    this.item = item
    this.props = props
  }

  public File nextFile(File f) {
    if (props != null) {
      props.doLastPath = item.buildPath(item, props)
    }

    return f
  }

  public boolean resume() {
    false
  }
}


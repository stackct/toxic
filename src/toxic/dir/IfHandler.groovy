package toxic.dir

import org.apache.log4j.Logger
import toxic.groovy.GroovyEvaluator

/*
  A file ending with .if will be groovy evaluated and if the result of
  that evaluation returns false or null then the remaining children in
  this directory will be skipped.
*/
public class IfHandler implements DirItemHandler {
  private static Logger log = Logger.getLogger(IfHandler.class.name)  

  DirItem item
  def props

  public IfHandler(DirItem item, props) {
    this.item = item
    this.props = props
  }

  public File nextFile(File f) {
    def result

    try {
      result = GroovyEvaluator.eval(f, props)
    } catch (Throwable t) {
      log.error("Failed to evaluate .if file, assuming false; file=${f?.name}", t)
    }

    if (!result) {
      log.debug("'If' test did not pass for file ${f.path}, ignoring remaining siblings.")
      return null
    }

    return item.nextChild(props)
  }

  public boolean resume() {
    true
  }
}


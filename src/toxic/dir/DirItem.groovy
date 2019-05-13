
package toxic.dir

import org.apache.log4j.Logger

/**
 * Represents a single file in the directory structure traversed by the
 * DirOrganizer.  If the file is a directory then it's children will also
 * be traversed for each call to nextFile.
 */
public class DirItem implements Comparable<DirItem> {
  private static Logger log = Logger.getLogger(DirItem.class.name)
  DirItem parent
  def file
  def children = []
  def childIndex = 0
  def winnerSelected = false
  def winnerValue = false
  def randomGenerator = new Random()

  public DirItem(String name, DirItem parent = null) {
    this(new File(name), parent)
  }

  public DirItem(File f, DirItem parent = null) {
    if (!f) {
      throw new IllegalArgumentException("Input file must not be null")
    }
    this.parent = parent
    file = f
  }

  public boolean exists() {
    return file?.exists()
  }

  public int compareTo(DirItem di) {
    return file?.name?.compareTo(di?.file?.name)
  }

  protected File nextChild(def props) {
    def f
    while (!f && (childIndex < children.size())) {
      def nextItem = children[childIndex]
      f = nextItem.nextFile(props)
      if (!f || !nextItem.file) {
        childIndex++
      }
    }
    return f
  }

  def random = {
    randomGenerator.nextInt(100)
  }

  protected boolean winning(File f) {
    if (winnerSelected) return winnerValue
    boolean winner = true
    def name = f?.name
    if (name.contains("_percent")) {
      def parts = name.tokenize("_")
      if (parts[0].isNumber()) {
        def odds = parts[0].toInteger()
        def r = random()
        winner = r < odds
      }
    }
    winnerSelected = true
    winnerValue = winner
    return winner
  }

  protected boolean shouldIgnore() {
    file?.name?.endsWith(".ignore") || file?.name?.endsWith(".disabled")
  }

  protected boolean isDirectory() {
    file?.isDirectory()
  }

  protected boolean isLink() {
    file?.name?.endsWith(".ln") || file?.name?.endsWith(".link")
  }

  protected boolean isLinkedSuite() {
    file?.name?.endsWith(".suite")
  }

  protected boolean isDep() {
    file?.name?.endsWith('.dep')
  }

  public File nextFile(def props = null) {
    if (!winning(file)) return null
    
    if (shouldIgnore()) {
      log.debug("Ignoring file; file=" + file.name)
      return null
    }

    def handler = DirItemHandlerFactory.make(this, props)

    File nextFile = handler.nextFile(file)

    if (!handler.resume()) {
      file = null
    }

    if (!nextFile && props?.pushpop) {
      props.pop()
      log.debug("Popped memory stack; stackSize=${props.stackSize()}; parent=${parent?.file}")
    }
    
    return nextFile
  }

  /**
   * Builds a path-like string representing the container of this current task DirItem.
   * This is not the same as the current task file.path since this task could have
   * been revisted several times via link files.
   */
  protected String buildPath(DirItem item, def props) {
    def path = ""
    def delim = props?.doPathDelimiter ?: "/"
    while (item?.parent) {
      item = item.parent
      if (path) {
        path = delim + path
      }
      path = item.file.name + path
    }
    return path
  }
}

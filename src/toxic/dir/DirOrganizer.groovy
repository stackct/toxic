
package toxic.dir

import toxic.MissingFileTask
import toxic.Task
import toxic.TaskOrganizer
import org.apache.log4j.Logger

/**
 * The Directory TaskOrganizer traverses through a directory and it's
 * subdirectories, attempting to find any valid task.  Tasks are returned
 * to the caller via next() in ascending alphabetical order, including
 * directories.  When a directory is encountered, it will be traversed
 * and it's tasks returned, before the next sibling task is returned.
 * <p>
 * Note that this class is not thread safe.  Only one thread should be
 * utilizing a single instance of this class at a time.
 */
public class DirOrganizer implements TaskOrganizer {
  protected static Logger slog = Logger.getLogger(DirOrganizer.class.name)
  File runRemoteFunctionsFile = new File(getClass().getResource('/toxic/library/run_remote_functions.groovy').toURI())
  File runRemoteFile = new File(getClass().getResource('/toxic/library/run_remote.groovy').toURI())
  def props
  DirItem dirItem
  Task nextTask = null
  def doDirs = [] as List
  def doDirIndex = 0

  protected def getLog() {
    return props?.log ?: this.slog
  }

  public void init(def props) {
    this.props = props

    // TODO The dirOrganizerInitialized check was added because
    // test/chaos/100_run/suites/300_spreadChaos/100_spawn_threads.groovy is cloning a the memory map
    // and invoking a new ToxicAgent directly, which causes unknown side effects
    // when the functions and deps dirs are rerun with a cloned map.
    if(props.homePath && !props.dirOrganizerInitialized) {
      String toxicPath = props.toxicPath ?: new File(props.homePath, 'toxic').absolutePath
      props.libPath = props.libPath ?: new File(toxicPath, 'library').absolutePath
      addDoDir(props.fnDir, new File(toxicPath, 'functions'))
      addDoDir(props.depsDir, new File(toxicPath, 'deps'))
      props.dirOrganizerInitialized=true
    }

    addDoDir(runRemoteFunctionsFile)
    if(props['toxic.remote']) {
      initRunRemote()
    }
    else {
      props.findAll { k, v -> k.startsWith("doDir") && v }.sort { a, b -> a.key <=> b.key }.each { addDoDir(it.value) }
    }

    dirItem = nextDirItem()

    if (!dirItem?.exists()) {
      throw new IllegalArgumentException("Invalid directory item name specified; doDirs=${doDirs}")
    }
  }

  void initRunRemote() {
    props['toxic.remote.arg.useDepsCache'] = true
    props.findAll { k, v -> k.startsWith('toxic.remote.arg.') }.each { k, v ->
      props['toxic.remote.args'][k - 'toxic.remote.arg.'] = v
      props.remove(k)
    }
    props['toxic.remote.doDir'] = props.doDir - (props.homePath + '/')
    addDoDir(runRemoteFile)
  }

  void addDoDir(String doDir) {
    addDoDir(new File(doDir))
  }

  void addDoDir(File doDir) {
    addDoDir(null, doDir)
  }

  void addDoDir(def propertyOverride, File defaultFile) {
    File file = propertyOverride ? new File(propertyOverride) : defaultFile
    if((file.isDirectory() && file.list().length > 0) || (!file.isDirectory() && file.exists())) {
      doDirs << file.absolutePath
    }
    else {
      log.warn("skipping non-existent or empty doDir; doDir=${file.absolutePath}")
    }
  }
  
  /**
   * Returns the task class name for the given filename.
   * For example, if the filename is 1_req.http, it will lookup
   * the property value that maps to the key tmTaskClass.http.
   */
  public def lookupTaskProperty(def name) {
    def key = "doTaskClass."
    def idx = name.lastIndexOf(".")
    if (idx > 0) {
      key += name.substring(idx + 1)
    }
    return key
  }

  /**
   * Initializes an implementation of the Task interface.
   *
   * @throws IllegalArgumentException if there is no task class defined for the
   *  input file
   */
  public def initTask(def input) {
    // Map the file extension into a Task class
    Class c = props.resolveClass(lookupTaskProperty(input.name))
    if (c == null) {
      // If the input is a file and it does not exist, then use a stand-in task.
      if(input instanceof File && !input.exists()) {
        def mft =  new MissingFileTask()
        mft.init(input, props)
        return mft
      }
      throw new IllegalArgumentException("No Task class is defined for task file; taskFilename=" + input.name)
    }

    // If the input is a file and it does not exist, then use a stand-in task.
    if(input instanceof File && !input.exists()) {
      def mft =  new MissingFileTask()
      mft.init(input, props)
      return mft
    }

    log.debug("Initializing task; taskClassName=" + c.name + "; inputName=" + input.name)
    def module = c.newInstance()
    module.init(input, props)
    return module
  }
  
  def nextDirItem() {
    def di
    if (doDirs && doDirIndex < doDirs.size()) {
      di = new DirItem(doDirs[doDirIndex++])
    }
    return di
  }

  def nextFile() {
    def f = dirItem?.nextFile(props)
    if (!f) {
      dirItem = nextDirItem()
      if(dirItem) { f = nextFile() }
    }
    return f
  }

  public boolean hasNext() {
    def result = false
    def f = nextFile()
    while (f && !result) {
      if (f) {
        try {
          nextTask = initTask(f)
          result = true
        } catch (IllegalArgumentException iae) {
          log.debug("Unsupported task file; taskFilename=" + f.name + "; details=" + iae.getMessage())
          f = nextFile()
        }
      }
    }
    return result
  }

  public Task next() {
    if (!nextTask) {
      hasNext()
    }

    def curTask = nextTask
    nextTask = null
    return curTask
  }
}

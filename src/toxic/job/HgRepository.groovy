package toxic.job

import org.apache.log4j.Logger

public class HgRepository extends ChangesetUrlResolver implements SourceRepository {

  static Logger log = Logger.getLogger(HgRepository.class.name)

  private static String DEFAULT_BRANCH = "default"

  private String local
  private String remote
  private String branch
  private String changesetUrlTemplate
  private boolean initialized

  public HgRepository(String local, String remote, String changesetUrlTemplate, String branch = null) {
    this.local = local
    this.remote = remote
    this.changesetUrlTemplate = changesetUrlTemplate ?: "${remote}/rev/@@changeset@@"
    this.branch = branch ?: DEFAULT_BRANCH
  }

  public List collectChanges() {
    getRevisions("hg in -b ${branch} -R ${local} ${remote}")
  }

  private List parseRevisions(String out) {
    def revisions = []
    if (out.contains("changeset")) {
      def change
      out.split("\n").each { line ->
        if (line.startsWith("changeset:")) {
          change = [:]
          revisions << change
        }
        if (line.contains(":") && change != null) {
          int idx = line.indexOf(":")
          change[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
      }
      revisions.each {
        def idx = it.changeset?.indexOf(":")
        if (idx >= 0) {
          it.changesetUrl = resolve(changesetUrlTemplate, remote.tokenize('/')[-1], it.changeset.substring(idx+1))
        }

        it.user = it.user?.trim()
        it.name = it.user
        it.email = it.user
        idx = it.user?.indexOf("<")
        if (idx >= 0 && it.user.lastIndexOf(">") > idx) {
          it.name = it.user.substring(0, idx).trim()
          it.email = it.user[(idx+1)..-2].trim()
        }
        idx = it.email?.indexOf("@")
        if (idx > 0) {
          it.user = it.email.substring(0, idx)
        } else {
          it.email = null
        }
      }
    }
  }

  public List getRevisionsByKeyword(String keyword) {
    getRevisions("hg log -b ${branch} -R ${local} -k ${keyword}") 
  }

  public List getRevisions() {
    getRevisions("hg log -b ${branch} -R ${local}")
  }

  public List getRevisions(String cmd) {
    init()
    def out = exec(cmd)?.output?.toString()
    parseRevisions(out)
  }

  public boolean hasChanges() {
    collectChanges()
  }

  public List update() {
   init()
   def changes = collectChanges()
   if (changes) {
     exec("hg pull -R ${local} ${remote}")
     exec("hg update -C ${branch} -R ${local}")
   }
   return changes
  }

  public String getDiff(String changeset) {
    exec("hg log -R ${local} -p -r ${changeset}", true)?.output?.toString()
  }

  private void init() {
    if (initialized) return
    exec("hg init ${local}", true)
    initialized = true
  }

  protected Map exec(String cmd, boolean failQuietly = false) {
    log.debug("executing hg command +++ local=${local}; remote=${remote}; branch=${branch}; cmd=${cmd}")

    def stdout = new StringBuffer()
    def stderr = new StringBuffer()
    def proc
    synchronized(this) {
      proc = Runtime.runtime.exec(cmd)
      proc.waitForProcessOutput(stdout, stderr)
    }

    log.debug("hg command result +++ local=${local}; remote=${remote}; cmd=${cmd}; exitValue=${proc.exitValue()}")

    if (stderr && !failQuietly) {
      throw new HgCommandException(stderr.toString())
    }

    [output:stdout, error:stderr, exitValue: proc.exitValue()]
  }

  public boolean equals(Object obj) {
    if (!obj) return false
    if (is(obj)) return true
    if (obj.local == local && obj.remote == remote && obj.branch == branch) return true
    return false
  }

  public int hashCode() {
    return ((local ?: "") + (remote ?: "") + (branch ?: "")).hashCode()
  }
}

class HgCommandException extends Exception {
  public HgCommandException(String msg) {
    super(msg)
  }
}

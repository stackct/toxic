package toxic.job

import org.apache.log4j.Logger

public class GitRepository extends ChangesetUrlResolver implements SourceRepository {

  static Logger log = Logger.getLogger(GitRepository.class.name)

  private static String DEFAULT_BRANCH = "master"

  private String local
  private String remote
  private String changesetUrlTemplate
  private String branch

  public GitRepository(String local, String remote, String changesetUrlTemplate, String branch = null) {
    this.local = local
    this.remote = remote
    this.changesetUrlTemplate = changesetUrlTemplate ?: 'javascript:void(0);'
    this.branch = branch ?: DEFAULT_BRANCH
  }

  public boolean hasChanges() {
    collectChanges()
  }

  public List update() {
    collectChanges().with { changes ->
      if (changes) {
        checkoutTargetBranch()
        exec("git pull", false)
      }

      // Handle submodules (if any)
      exec("git submodule update --recursive --remote --init")
      exec(['git', 'submodule', 'foreach', 'git checkout master && git pull --rebase'], false)

      return changes
    }
  }

  public List collectChanges() {
    init()

    def format = "%H%x09%aD%x09%an%x09%ae%x09%s"

    exec("git fetch --all", true)
    String logBranch = "@{u}"
    // If we are about to change to a different branch, parse the change log from the remote branch instead of the current branch
    if(!isOnExpectedBranch()) {
      verifyRemoteBranchExists()
      logBranch = remoteBranch()
    }
    exec("git --no-pager log --format=${format} ..${logBranch}", true).with { result ->
      if (result.exitValue == 0) {
        parseRevisions(result.output)
      }
    }
  }

  public String getDiff(String changeset) {
    exec("git show ${changeset}")?.output
  }

  public def checkoutTargetBranch(){
    exec("git checkout --track -B ${this.branch} ${remoteBranch()}", true)
    if (!isOnExpectedBranch()) {
      throw new GitCommandException(getCurrentBranch())
    }
  }

  private boolean isOnExpectedBranch() {
    return getCurrentBranch().contains("* ${this.branch}")
  }

  private String getCurrentBranch() {
    exec("git branch").output
  }

  private void verifyRemoteBranchExists() {
    if(0 != exec("git rev-parse --verify ${remoteBranch()}", true).exitValue) {
      throw new GitCommandException("${remoteBranch()} does not exist")
    }
  }

  private String remoteBranch() {
    "origin/${this.branch}"
  }

  protected List parseRevisions(String input) {
    def revisions = []

    input.split('\n').each { line ->
      if (line) {
        line.split('\t').with { parts ->
          def revision = [:]
          revision['changesetUrl'] = resolve(changesetUrlTemplate, remote.tokenize('/')[-1], parts[0])
          revision['changeset']    = parts[0]
          revision['date']         = parts[1]
          revision['user']         = parts[2]
          revision['name']         = parts[2]
          revision['email']        = parts[3]
          revision['summary']      = parts[4]

          revisions << revision
        }
      }
    }

    revisions
  }

  protected boolean init() {
    if (!initialized) {
      exec("git clone --recurse-submodules ${remote} ${this.local}", true).exitValue == 0
    }
  }

  protected boolean isInitialized() {
    exec("git rev-parse", true).exitValue == 0
  }

  protected Map exec(String cmd, boolean failQuietly = false) {
    exec(cmd.split(" ") as List, failQuietly)
  }

  protected Map exec(List cmdAndArgs, boolean failQuietly = false) {
    def cmds = cmdAndArgs

    log.debug("executing git command +++ local=${local}; remote=${remote}; branch=${branch}; cmd=${cmdAndArgs}")

    def stdout = new StringBuffer()
    def stderr = new StringBuffer()
    def proc

    synchronized(this) {
      ProcessBuilder pb = new ProcessBuilder(cmds)
      pb.directory(new File(local))
      proc = pb.start()
      proc.waitForProcessOutput(stdout, stderr)
    }

    log.debug("git command result +++ local=${local}; remote=${remote}; cmd=${cmds}; exitValue=${proc.exitValue()}")

    if (proc.exitValue() != 0) {
      log.debug("git command returned stderr; local=${local}; remote=${remote}; cmd=${cmds}; exitValue=${proc.exitValue()}; stderr=${stderr.toString()}")

      if (!failQuietly) {
        throw new GitCommandException(stderr.toString())
      }
    }

    [output:stdout.toString(), error:stderr.toString(), exitValue: proc.exitValue()]
  }
}

class GitCommandException extends Exception {
  public GitCommandException(String msg) {
    super(msg)
  }
}

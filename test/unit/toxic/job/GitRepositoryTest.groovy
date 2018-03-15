package toxic.job

import org.junit.*
import groovy.mock.interceptor.*
import com.google.common.io.Files

public class GitRepositoryTest {

  def repoCommits = [:]

  @Test
  public void should_initialize_repository_on_first_poll_for_changes() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        def repo = new GitRepository(local, remote, null)

        assert repo.initialized == false
        assert repo.hasChanges() == false
        assert repo.initialized == true
      }
    }
  }

  @Test
  public void should_clone_repository_with_submodules() {
    withRepoMock(true) { common ->
      withRepoMock(false) { local -> 
        withRepoMock(true) { remote -> 
          addSubmodule(common, remote)
          new GitRepository(local, remote, null).init()
          assert new File("${local}/common/FROM_COMMON").exists()
        }
      }
    }
  }

  @Test
  public void should_update_repository() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->

        addCommit(remote)

        def repo = new GitRepository(local, remote, null)
        repo.init()

        addCommit(remote)
        addCommit(remote)
        addCommit(remote)

        def changes = repo.update()

        assert changes.size() == 3
      }
    }
  }

  @Test
  public void should_update_repository_with_submodules() {
    withRepoMock(true) { common ->
      withRepoMock { local ->
        withRepoMock(true) { remote ->
          addCommit(remote)
          addSubmodule(common, remote)

          def repo = new GitRepository(local, remote, null)
          repo.init()
          
          addCommit(common, "FROM_COMMON_NEW")
          repo.update()
          
          assert new File("${local}/common/FROM_COMMON_NEW").exists()
        }
      }
    }
  }

  @Test
  public void should_checkout_the_target_branch() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        addCommit(remote)

        def repo = new GitRepository(local, remote, null)
        repo.init()

        repo.checkoutTargetBranch()

        assert localExec("git branch", local).output.contains("* master")
      }
    }
  }

  @Test
  public void update_will_fail_if_branch_is_incorrect() {
    def exception = null

    withRepoMock { local ->
      withRepoMock(true) { remote ->
        addCommit(remote)

        def repo = new GitRepository(local, remote, null, "version_2")
        repo.init()

        addCommit(remote)
        addCommit(remote)
        addCommit(remote)

        try {
          repo.update()
        }
        catch (GitCommandException ex) {
          exception = ex
        }
      }
    }

    assert null != exception
  }

  class GitRepositoryWithSeededChanges extends GitRepository {
    public List seededChanges = []

    public GitRepositoryWithSeededChanges(List seededChanges, String local, String remote, String changesetUrlTemplate, String branch = null){
      super(local, remote, changesetUrlTemplate, branch)
      this.seededChanges = seededChanges
    }

    public List collectChanges() { seededChanges }
  }

  @Test
  public void should_throw_exception_if_update_fails() {
    def exception = null
    def exitCode = 1
    def mock = new MockFor(ProcessBuilder)
    mock.ignore.directory { file -> }
    mock.ignore.start { -> [waitForProcessOutput: { x,y -> y.append("err") }, exitValue: { -> exitCode }] }

    mock.use {
      try {
        def repo = new GitRepositoryWithSeededChanges(["change 1", "change 2"], "local", "remote", "foo")

        repo.update()
      }
      catch (GitCommandException ex) {
        exception = ex
      }
    }

    assert null != exception
  }

  @Test
  public void should_parse_revisions() {
    def input = new StringBuffer()

    input.append('be5536bc7af259172acf14eaee3feb2e8f57d271\tThu, 18 May 2017 20:58:35 -0400\tFirst Last\tsample@mycompany.invalid\tFirst change.' + '\n')
    input.append('dd1a1dd2926723b311da9566ada30990cfb39799\tThu, 18 May 2017 19:07:18 -0400\tFirst Last\tsample@mycompany.invalid\tSecond change.')

    new GitRepository("local", "remote", null).parseRevisions(input.toString()).with { revisions ->
      assert revisions.size() == 2

      assert revisions[0].changeset == 'be5536bc7af259172acf14eaee3feb2e8f57d271'
      assert revisions[0].changesetUrl == 'javascript:void(0);'
      assert revisions[0].user == 'First Last'
      assert revisions[0].name == 'First Last'
      assert revisions[0].email == 'sample@mycompany.invalid'
      assert revisions[0].date == 'Thu, 18 May 2017 20:58:35 -0400'
      assert revisions[0].summary == 'First change.'

      assert revisions[1].changeset == 'dd1a1dd2926723b311da9566ada30990cfb39799'
      assert revisions[1].changesetUrl == 'javascript:void(0);'
      assert revisions[0].user == 'First Last'
      assert revisions[0].name == 'First Last'
      assert revisions[0].email == 'sample@mycompany.invalid'
      assert revisions[1].date == 'Thu, 18 May 2017 19:07:18 -0400'
      assert revisions[1].summary == 'Second change.'
    }
  }

  @Test
  public void should_build_changeset_url_from_template() {
    def scenarios = [
      [template: null, expected: 'javascript:void(0);'],
      [template: 'http://go/@@wrongrepo@@/@@wrongcommit@@', expected: 'http://go/@@wrongrepo@@/@@wrongcommit@@'],
      [template: 'http://go/@@repo@@/@@changeset@@', expected: 'http://go/remote/be5536bc7af259172acf14eaee3feb2e8f57d271']
    ]

    def input = 'be5536bc7af259172acf14eaee3feb2e8f57d271\tThu, 18 May 2017 20:58:35 -0400\tFirst Last\tsample@mycompany.invalid\tFirst change.' + '\n'

    scenarios.each { sc ->
      new GitRepository("local", "hg/remote", sc.template).parseRevisions(input).with { revisions ->
        assert revisions[0].changesetUrl == sc.expected
      }
    }
  }

  @Test
  public void should_determine_if_repo_has_changes() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        addCommit(remote)
        
        def repo = new GitRepository(local, remote, null)
        assert !repo.hasChanges()

        addCommit(remote)
        addCommit(remote)
        addCommit(remote)

        assert repo.hasChanges()
      }
    }
  }

  @Test
  public void should_collect_changes() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        addCommit(remote)
        
        def repo = new GitRepository(local, remote, null)
        assert !repo.hasChanges()

        addCommit(remote)
        addCommit(remote)
        addCommit(remote)

        assert repo.collectChanges().size() == 3
      }
    }
  }

  @Test
  public void should_determine_if_directory_is_git_repo() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        assert !new GitRepository(local, remote, null).initialized
      }
    }

    withRepoMock { local ->
      withRepoMock(true) { remote ->
        def repo = new GitRepository(local, remote, null)
        repo.init()
        assert repo.initialized
      }
    }
  }

  @Test
  public void should_throw_exception_if_process_fails() {
    assert checkException(128) instanceof GitCommandException
  }

  @Test
  public void should_not_throw_exception_if_process_fails_silently() {
    assert checkException(128, true) == null
  }

  @Test
  public void should_get_diff() {
    withRepoMock { local ->
      withRepoMock(true) { remote ->
        addCommit(remote)
        
        def repo = new GitRepository(local, remote, null)
        repo.update()

        repo.getDiff(repoCommits[remote][0]).with { commit ->
          assert commit.contains('Author: John <johndoe@example.com>')
          assert commit.contains('@@ -0,0 +1 @@')
          assert commit.contains('+bar')
        }
      }
    }
  }

  private Exception checkException(exitCode, failSilently=false) {
    def exception

    def mock = new MockFor(ProcessBuilder)
    mock.demand.directory { file -> }
    mock.demand.start { -> [waitForProcessOutput: { x,y -> y.append("err") }, exitValue: { -> exitCode }] }
    
    mock.use {
      try {
        new GitRepository("local", "remote", "foo").exec('git rev-parse', failSilently)
      }
      catch (GitCommandException gce) {
        exception = gce
      }
    }
    exception
  }

  private withRuntimeMock(exitValue, out, err, closure) {
    def stub = [:]
    stub.exitValue = { -> exitValue }
    stub.waitForProcessOutput = { o,e ->
      if (out) o.append(out)
      if (err) e.append(err)
    }

    def mock = new MockFor(Runtime)
    mock.ignore.getRuntime { [exec: { String cmd -> stub }] }
    mock.use {
      closure.call()
    }
  }

  private void withRepoMock(initialize=false, closure) {
    def dir = createMockRepo(initialize)

    try {
      closure(dir)
    } finally {
      destroyMockRepo(dir)
    }
  }

  private def createMockRepo(initialize) {
    def dir = Files.createTempDir().path

    if (initialize) {
      initMockRepo(dir)
    }

    dir
  }

  private void initMockRepo(dir) {
    localExec("git init", dir)
  }

  private void addCommit(dir, filename="foo.${UUID.randomUUID()}") {
    repoCommits[dir] = repoCommits[dir] ?: []
    
    def newFile = new File(dir, filename)
    newFile.write("bar")
    
    localExec('git config user.name "John Doe"', dir)
    localExec('git config user.email johndoe@example.com', dir)
    localExec('git add .', dir)
    localExec('git commit -m "commit"', dir)
    def lastCommit = localExec('git --no-pager log -n1 --format=%H', dir)
    
    repoCommits[dir] << lastCommit?.output.trim()
 }

 private void addSubmodule(sub, dir) {
  addCommit(sub, "FROM_COMMON")
  localExec("git submodule add ${sub} common", dir)
  localExec("git add .", dir)
  localExec("git commit -m submodule", dir)
}

  private void destroyMockRepo(dir) {
    new File(dir).deleteDir()
  }

  private def localExec(cmd, dir) {
    new GitRepository(dir, null, null, null).exec(cmd)
  }
}
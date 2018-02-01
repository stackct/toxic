package toxic.job

import org.junit.*
import groovy.mock.interceptor.*

public class SourceRepoMonitorTest {
  def repoChanged = [:]

  @Test
  void should_start_and_stop() {
    def notified
    def mock = new StubFor(Object)
    mock.demand.notify(1) { notified = true }
    mock.use {
      SourceRepoMonitor.instance.start()
      assert SourceRepoMonitor.instance.isRunning()
      SourceRepoMonitor.instance.stop()
      sleep(1000)
      assert !SourceRepoMonitor.instance.running
      assert notified
    }
  } 

  @Test
  void should_check_repos() {
    def repo1 = mockRepo('repo1', false)
    def repo2 = mockRepo('repo2', true)

    SourceRepoMonitor.instance.repoUpdatedMap.clear()

    assert !SourceRepoMonitor.instance.hasRepoChanged(repo1)
    assert !SourceRepoMonitor.instance.hasRepoChanged(repo1)
    assert SourceRepoMonitor.instance.hasRepoChanged(repo2)
    assert SourceRepoMonitor.instance.hasRepoChanged(repo2)
    assert !SourceRepoMonitor.instance.hasRepoChanged(repo1)
    assert !SourceRepoMonitor.instance.hasRepoChanged(repo1)
    assert SourceRepoMonitor.instance.hasRepoChanged(repo2)
    assert SourceRepoMonitor.instance.hasRepoChanged(repo2)

    // If the underlying repo has not actually changed make sure we get an accurate value.
    repoChanged.repo2 = false
    assert !SourceRepoMonitor.instance.hasRepoChanged(repo2)

    sleep(5000)
     // should have checked at least once initially, then possibly up to 10 more times (1000 second sleep / 100ms wait = 10)
     // depending on CPU time allocation to thread. 2017-03-07 JBE - removed 10x limit since sometimes the build server is sluggish and the
     // sleep 1000 actually ends up pausing for longer than that time.
    assert repo1.checked > 1 
    assert repo2.checked > 1 
    assert SourceRepoMonitor.instance.isRunning()
  }

  def mockRepo(repoId, initialChangedState) {
    repoChanged[repoId] = initialChangedState
    new SourceRepository() {
      def checked = 0
      List update() {}
      List collectChanges() {}
      boolean hasChanges() { checked++; return repoChanged[repoId] }
      String getDiff(String changeset) { "" }
    }
  }
}
package toxic.job

import org.junit.*
import toxic.notification.*
import groovy.mock.interceptor.*
import util.*

public class PauseManagerTest {
  def subscriber
  def notifications

  @Before
  public void before() {
    def events = [EventType.PROJECT_PAUSED, EventType.PROJECT_UNPAUSED]
    notifications = [:]
    events.each { e -> notifications[e] = [] }
    subscriber = [handle: { n -> notifications[n.type] << n }] as Subscriber
    NotificationCenter.instance.subscribe(subscriber, events)
  }

  @After
  public void after() {
    PauseManager.instance.reset()
  }

  @Test
  void should_pause_unpause() {
    mockDir { tempDir ->
      assert !PauseManager.instance.isProjectPaused("pmtest")
      def jobManager = new JobManager("url") { String getConfigDir() { tempDir.absolutePath } }
      PauseManager.instance.with { pm ->
        pm.pauseProject(jobManager, "pmtest")
        assert pm.isProjectPaused("pmtest")

        // Should be a no-op
        pm.pauseProject(jobManager, "pmtest")
        assert pm.isProjectPaused("pmtest")

        assert pm.hasProjectPauseToggledSince(new Date() - 1, "pmtest")
        assert !pm.hasProjectPauseToggledSince(new Date() + 1, "pmtest")
        assert !pm.isProjectPaused("pmtest2")

        pm.unpauseProject(jobManager, "pmtest")
        assert !pm.isProjectPaused("pmtest")
        assert !pm.isProjectPaused("pmtest2")

        // Should be a no-op
        pm.unpauseProject(jobManager, "pmtest")
        assert !pm.isProjectPaused("pmtest")
      }
    }
  }

  @Test
  void should_unpause_all() {
    mockDir { tempDir ->
      def jobManager = new JobManager("url") {
        String getConfigDir() { tempDir.absolutePath }
      }
      PauseManager.instance.with { pm ->
        pm.pauseProject(jobManager, "upatest1")
        pm.pauseProject(jobManager, "upatest2")
        assert pm.isProjectPaused("upatest1")
        assert pm.isProjectPaused("upatest2")

        pm.unpauseProjects(jobManager)
        assert !pm.isProjectPaused("upatest1")
        assert !pm.isProjectPaused("upatest2")
      }
    }
  }

  @Test
  void should_notify_when_project_is_paused_and_unpaused() {
    mockDir { tempDir ->
      def jobManager = new JobManager("url") {
        String getConfigDir() { tempDir.absolutePath }
      }

      PauseManager.instance.with { pm ->
        pm.pauseProject(jobManager, "foo")
        pm.pauseProject(jobManager, "bar")

        pm.unpauseProject(jobManager, "foo")
      }

      // Wait for notifications to be sent
      long startTime = System.currentTimeMillis()
      Wait.on { -> notifications.size() >= 2 || System.currentTimeMillis() - startTime > 5000 }.start()

      assert notifications[EventType.PROJECT_PAUSED][0].data.project in ['foo', 'bar']
      assert notifications[EventType.PROJECT_PAUSED][0].data.paused == true
      assert notifications[EventType.PROJECT_PAUSED][1].data.project in ['foo', 'bar']
      assert notifications[EventType.PROJECT_PAUSED][1].data.paused == true

      assert notifications[EventType.PROJECT_UNPAUSED][0].data.project == 'foo'
      assert notifications[EventType.PROJECT_UNPAUSED][0].data.paused == false
    }
  }

  def mockDir(Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      c(tempDir)
    }
    finally {
      tempDir?.deleteDir()
    }
  }
}

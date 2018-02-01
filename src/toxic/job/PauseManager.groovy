package toxic.job

import toxic.notification.*

@Singleton
class PauseManager implements Publisher {
  private def pausedProjects = [:]
  private boolean loaded

  void reset() {
    pausedProjects = [:]
    loaded = false
  }

  synchronized void load(JobManager mgr) {
    if (!loaded) {
      ConfigManager.instance.read(mgr, this.class.simpleName).each { project, paused ->
        pausedProjects[project] = new PauseInfo(project)
        if (paused) {
          pausedProjects[project].pause()
        } else {
          pausedProjects[project].unpause()
        }
      }
      loaded = true
    }
  }

  private def save(JobManager mgr) {
    def map = [:]
    pausedProjects.each { project, info ->
      if (project && info) map[project] = info.isPaused()
    }
    ConfigManager.instance.write(mgr, this.class.simpleName, map)    
  }

  synchronized PauseInfo getPauseInfo(String project) {
    def pausedInfo = pausedProjects[project]
    if (!pausedInfo) {
      pausedInfo = new PauseInfo(project)
      pausedProjects[project] = pausedInfo
    }
    return pausedInfo    
  }

  def pauseProject(JobManager mgr, String project) {
    if (isProjectPaused(project)) return
    
    getPauseInfo(project).pause()
    save(mgr)

    use (NotificationCenter) {
      notify(EventType.PROJECT_PAUSED, [project:project, paused:true])
    }
  }
  
  def unpauseProject(JobManager mgr, String project) {
    if (!isProjectPaused(project)) return

    getPauseInfo(project).unpause()
    save(mgr)

    use (NotificationCenter) {
      notify(EventType.PROJECT_UNPAUSED, [project:project, paused:false])
    }
  }

  synchronized def unpauseProjects(JobManager mgr) {
    pausedProjects.each { k, v ->
      unpauseProject(mgr, k)
    }
  }

  synchronized boolean isProjectPaused(String project) {
    return getPauseInfo(project).isPaused()
  }  

  synchronized boolean hasProjectPauseToggledSince(Date since, String project) {
    return since <= getPauseInfo(project).getToggleDate()
  }
}
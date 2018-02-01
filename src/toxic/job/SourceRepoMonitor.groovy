package toxic.job

import org.apache.log4j.*

@Singleton
class SourceRepoMonitor {
  protected static Logger log = Logger.getLogger(this)

  boolean running
  def repoUpdatedMap = [:]
  def lock = new Object()

  public boolean isRunning() {
    return running
  }

  public boolean hasRepoChanged(SourceRepository repo) {
    def changed
    if (!repoUpdatedMap.containsKey(repo)) {
      // This repo hasn't been checked yet, do it now.
      changed = checkRepo(repo)
    } else {
      // This repo has been checked already, find the cached changed indicator
      changed = repoUpdatedMap[repo]

      // We think this repo has changed; but it's a cached indicator. Let's double check
      // in case another job has already kicked off and pull the changes. This will avoid
      // multiple quick jobs being kicked off for the same change.
      if (changed) {
        changed = checkRepo(repo)
      }
    }
    return changed
  }

  private boolean checkRepo(SourceRepository repo) {
    def changed = repo.hasChanges()

    synchronized(this) {
      if (!repoUpdatedMap) start()
      repoUpdatedMap[repo] = changed
    }

    return changed
  }

  public synchronized void start() {
    if (!running) {
      running = true
      Thread.startDaemon("repo-mon") {
        log.info("Started repo monitor thread")
        while (running) {
          try {
            log.info("Starting repo scan for changes; knownRepos=${repoUpdatedMap.size()}")
            def keys = []
            synchronized(this) {
              keys.addAll(repoUpdatedMap.keySet())
            }
            def changedRepos = []
            keys.each { 
              if (checkRepo(it)) {
                changedRepos << it
              }
              // Wait a second in between repo scans to avoid pummeling the repo server
              synchronized(lock) { lock.wait(1000) }
            }
            log.info("Finished repo scan for changes; totalRepos=${keys.size()}; changedReposCount=${changedRepos.size()}; changedRepos='${changedRepos.join(",")}'")
          } catch (Exception e) {
            log.error("Unexpected exception in repo monitor thread; reason='${e.message}'", e)
          }
        }
        log.warn("Exited repo monitor thread")
      }
    }
  }

  public void stop() {
    if (running) {
      log.info("Stopping repo monitor")
      running = false
      synchronized(lock) {
        lock.notify()
      }
    }
  }
}
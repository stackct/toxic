package toxic.job

import org.apache.log4j.*
import toxic.notification.*
import toxic.user.*

@Singleton
class AckManager implements Publisher {
  protected static Logger log = Logger.getLogger(AckManager.class)

  private Map ackedJobs = [:]
  
  private boolean loaded

  void reset() {
    ackedJobs = [:]
    loaded = false
  }

  synchronized void load(JobManager mgr) {
    if (!loaded) {
      ackedJobs = ConfigManager.instance.read(mgr, this.class.simpleName)
      loaded = true
    }
  }

  private def save(JobManager mgr) {
    ConfigManager.instance.write(mgr, this.class.simpleName, ackedJobs)
  }

  synchronized boolean ack(JobManager mgr, String jobMatch, String user) {
    def job = mgr.findJob(jobMatch)
    if (!job) {
      job = mgr.findLatestJob(jobMatch, JobStatus.COMPLETED)
    }

    if (!job) {
      log.warn("Could not find job '${jobMatch}'")
      return false
    }

    if (job.failed == 0) {
      log.warn("Cannot ack successful job '${job.id}'")
      return false
    }

    def existingAck = getAck(job.id)
    if (existingAck) {
      log.warn("Job '${job.id}' already acked by '${existingAck}'")
      return false 
    }

    def foundUser = UserManager.instance.getById(user)

    if (!foundUser) {
      log.warn("User not found '${user}'")
      return false
    }

    ackedJobs[job.id] = user

    save(mgr)

    use (NotificationCenter) {
      notify(EventType.JOB_ACKED, [id:job.id, acked:foundUser.id, mgr: mgr])
    }
    
    true
  }

  synchronized boolean resolve(JobManager mgr, String jobMatch, String user) {
    boolean resolved = true
    
    def job = mgr.findJob(jobMatch)
    if (!job) {
      job = mgr.findLatestJob(jobMatch, JobStatus.COMPLETED)
    }
    def jobId = job?.id

    def ack = getAck(jobId)

    if (!ack) {
      log.warn("No ack found for job '${jobId}'")
      return false
    }

    if (ack != user) {
      log.warn("User '${user}' cannot resolve ack for user '${ack}'")
      return false
    }

    ackedJobs.remove(jobId)
    save(mgr)
    
    use (NotificationCenter) {
      notify(EventType.JOB_RESOLVED, [id:jobId, acked:null])
    }

    PauseManager.instance.unpauseProject(mgr, job.project)

    resolved
  }

  synchronized String getAck(String jobId) {
    ackedJobs[jobId]
  }

  synchronized Map getAcks() {
    ackedJobs
  }
}

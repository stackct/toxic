package toxic.job

public interface JobNotification {
  /**
   * Returns true if the notification successfully executed.
   */
  public boolean execute(Job job)
}
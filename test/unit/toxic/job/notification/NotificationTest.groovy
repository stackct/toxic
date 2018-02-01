package toxic.notification

import org.junit.*

class NotificationTest {

  @Test
  public void should_compare_notifications() {
    def n1 = new Notification(EventType.JOB_ACKED, [:])
    def n2 = new Notification(EventType.JOB_RESOLVED, [:])

    assert n2 > n1
  }
}
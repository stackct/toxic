package toxic.notification

import java.util.concurrent.*

class NotificationCenterTestMixin {
  public void resetNotifications() {
    NotificationCenter.instance.reset()
  }

  public void stopNotifications() {
    NotificationCenter.instance.threadPool.shutdown()
    NotificationCenter.instance.threadPool.awaitTermination(120, TimeUnit.SECONDS)
  }
}
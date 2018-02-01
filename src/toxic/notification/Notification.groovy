package toxic.notification

import java.util.concurrent.atomic.AtomicInteger

class Notification implements Comparable {

  private static AtomicInteger globalSequence = new AtomicInteger()

  public EventType type
  public Map data
  private int seq

  public Notification(EventType type, Map data) {
    this.type = type
    this.data = data
    this.seq = globalSequence.incrementAndGet()
  }

  public int compareTo(Object that) {
    if (this.seq < that.seq) return -1
    if (this.seq > that.seq) return  1
    return 0
  }
}

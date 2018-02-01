package toxic.notification

import org.junit.*
import java.util.concurrent.*

@Mixin(NotificationCenterTestMixin)
public class EventCollectorTest {

  @Before
  @After
  public void before() {
    resetNotifications()
  }

  @Test
  public void should_subscribe_to_all_events() {
    def events = [
      EventType.JOB_ACKED,
      EventType.JOB_RESOLVED,
      EventType.PROJECT_PAUSED, 
      EventType.PROJECT_UNPAUSED
    ]

    def collector = new EventCollector()

    NotificationCenter.instance.publish(EventType.JOB_ACKED, [id:'foo-1', acked:'fred'])
    NotificationCenter.instance.publish(EventType.JOB_RESOLVED, [id:'foo-1', acked:null])
    NotificationCenter.instance.publish(EventType.PROJECT_PAUSED, [project:'foo'])
    NotificationCenter.instance.publish(EventType.PROJECT_UNPAUSED, [project:'foo'])
    
    stopNotifications()

    def collected = collector.all().collect { it.message }

    assert collected.find { it == "Job 'foo-1' was acked by 'fred'" }
    assert collected.find { it == "Job 'foo-1' was resolved" }
    assert collected.find { it == "Project 'foo' was paused" }
    assert collected.find { it == "Project 'foo' was unpaused" }
  }

  @Test
  public void should_limit_number_of_events() {
    def collector = new EventCollector()

    10.times { n ->
      NotificationCenter.instance.publish(EventType.JOB_ACKED, [id:'n-' + n, acked:'fred'])
    }

    stopNotifications()

    assert collector.all().size() == 5
  }
}

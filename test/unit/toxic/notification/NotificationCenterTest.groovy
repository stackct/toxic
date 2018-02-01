package toxic.notification

import util.*
import org.junit.*

@Mixin(NotificationCenterTestMixin)
public class NotificationCenterTest implements Publisher {
  
  @Before
  public void before() {
    resetNotifications()
  }

  @After
  public void after() {
    resetNotifications()
  }

  @Test
  public void should_allow_category_method() {
    def notified = []

    def sub1 = [handle: { n -> notified << "sub1" }] as Subscriber

    NotificationCenter.instance.with { n ->
      n.subscribe(sub1, EventType.JOBS_LOADED)

      use (NotificationCenter) {
        notify(EventType.JOBS_LOADED, [:])
      }
    }
    
    stopNotifications()

    assert notified == ['sub1']
  }

  @Test
  public void should_allow_subscribe() {
    def sub = [handle: { n -> }] as Subscriber

    NotificationCenter.instance.with { n ->
      n.subscribe(sub, EventType.JOBS_LOADED)
      n.subscribe(sub, EventType.JOBS_LOADED)
      n.subscribe(sub, EventType.JOB_CHANGED)
      
      assert n.subscribers[EventType.JOBS_LOADED] == [sub]
      assert n.subscribers[EventType.JOB_CHANGED] == [sub]

      n.reset()
      n.subscribe(sub, [EventType.JOBS_LOADED, EventType.JOB_CHANGED])

      assert n.subscribers[EventType.JOBS_LOADED] == [sub]
      assert n.subscribers[EventType.JOB_CHANGED] == [sub]
    }
  }

  @Test
  public void should_allow_unsubscribe() {
    def sub = [handle: { n -> }] as Subscriber
    
    NotificationCenter.instance.with { n ->
      n.subscribe(sub, EventType.JOBS_LOADED)
      n.unsubscribe(sub, EventType.JOBS_LOADED)

      assert n.subscribers == [:]

      n.subscribe(sub, EventType.JOBS_LOADED)
      n.subscribe(sub, EventType.JOB_CHANGED)
      n.unsubscribe(sub, EventType.JOBS_LOADED)

      assert n.subscribers[EventType.JOB_CHANGED] == [sub]
    }
  }

  @Test
  public void should_dispatch_published_message_to_all_subscribers() {
    def notified = []

    def sub0 = [handle: { n -> throw new CouldNotHandle('Oops!') }] as Subscriber
    def sub1 = [handle: { n -> notified << "sub1" }] as Subscriber
    def sub2 = [handle: { n -> notified << "sub2" }] as Subscriber

    NotificationCenter.instance.with { n ->
      n.subscribe(sub0, EventType.JOBS_LOADED)
      n.subscribe(sub1, EventType.JOBS_LOADED)
      n.subscribe(sub2, EventType.JOB_CHANGED)

      n.publish(EventType.JOBS_LOADED, [:])
    }

    stopNotifications()

    assert notified == ['sub1']
  }
}

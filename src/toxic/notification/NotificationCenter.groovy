package toxic.notification

import java.util.concurrent.*
import log.Log

@Singleton(strict=false)
public class NotificationCenter {
  private final static Log log = Log.getLogger(this)

  private ThreadPoolExecutor threadPool

  static void notify(Publisher publisher, EventType type, Map data) {
    NotificationCenter.instance.publish(type,data)
  }

  private NotificationCenter() {
    reset()
  }

  protected Map subscribers = [:]

  public void reset() {
    subscribers = [:]
    
    def factory = new ThreadFactory() {
      int count = 0
      public Thread newThread(Runnable r) {
        new Thread(r, "notify-${count++}").with { thread ->
          thread.setDaemon(true)
          return thread
        }
      }
    }
    
    threadPool = new ThreadPoolExecutor(20, 20, 60000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), factory)
  }

  public void subscribe(Subscriber subscriber, List<EventType> types) {
    types.each { t -> subscribe(subscriber, t) }
  }

  public synchronized void subscribe(Subscriber subscriber, EventType type) {
    if (!subscribers[type]) {
      subscribers.put(type, [])
    }
    
    if (!subscribers[type].find { s -> s.is(subscriber) }) {
      subscribers[type] << subscriber
      log.debug("${subscriber} now subscribed to '${type}'")
    }
  }

  public synchronized void unsubscribe(Subscriber subscriber, EventType type) {
    if (subscribers[type]) {
      subscribers[type].remove(subscriber)

      if (subscribers[type].size() == 0)
        subscribers.remove(type)
    }
  }

  private void publish(EventType type, Map data) {
    dispatch(type, new Notification(type, data))
  }

  private void dispatch(EventType type, Notification n) {
    subscribers[type].each { s -> 
      threadPool.submit(new Dispatcher(n,s))
    }
  }
}

class Dispatcher implements Runnable {
  private final static Log log = Log.getLogger(this)

  private Notification notification
  private Subscriber subscriber

  public Dispatcher(Notification n, Subscriber s) {
    this.notification = n
    this.subscriber = s
  }

  public void run() {
    log.debug("Dispatching notification; notification=${notification.type}; subscriber=${subscriber.class.name}")
    
    try {
      subscriber.handle(notification)
    }
    catch(Exception ex) {
      log.warn("Message dispatch failed; notification=${notification.type}; subscriber=${subscriber.class.name}; reason=${ex.message}")
    }
  }
}

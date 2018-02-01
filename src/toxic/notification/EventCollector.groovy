package toxic.notification

class EventCollector implements Subscriber {
  private List events = []
  private int maxEvents

  public List all() {
    return events
  }

  public EventCollector(int maxEvents = 5) {
    this.maxEvents = maxEvents

    NotificationCenter.instance.subscribe(this, [
      EventType.JOB_ACKED,
      EventType.JOB_RESOLVED,
      EventType.PROJECT_PAUSED, 
      EventType.PROJECT_UNPAUSED
    ])
  }

  void handle(Notification n) {
    addEvent(n)
  }

  synchronized addEvent(Notification n) {
    def message

    switch (n.type) {
      case EventType.JOB_ACKED:
        message = "Job '${n.data.id}' was acked by '${n.data.acked}'"
        break
      case EventType.JOB_RESOLVED:
        message = "Job '${n.data.id}' was resolved"
        break
      case EventType.PROJECT_PAUSED:
        message = "Project '${n.data.project}' was paused"
        break
      case EventType.PROJECT_UNPAUSED:
        message = "Project '${n.data.project}' was unpaused"
        break
      default:
        return
    }

    if (events.size() > maxEvents-1) {
      events = events[1..-1]
    }

    events << [ timestamp: new Date(), message: message ]
  }
}
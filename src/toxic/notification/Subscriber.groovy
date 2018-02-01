package toxic.notification

public interface Subscriber {
  void handle(Notification notification)
}

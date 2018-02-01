package toxic.user

public interface UserSource {
  public User getById(String id)
  public User find(String id, Map options)
}
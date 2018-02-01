
package toxic

public interface TaskOrganizer {
  public void init(def props)
  public boolean hasNext()
  public Task next()
}
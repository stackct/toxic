
package toxic

public interface TaskResultsFormatter {
  public void init(def props)
  public void format(List<TaskResult> results)
}

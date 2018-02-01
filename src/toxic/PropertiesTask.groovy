
package toxic

public class PropertiesTask extends Task {
  def inputProps

  public List<TaskResult> doTask(def memory) {
    if (input instanceof File) {
      memory.propertiesFile = input
      memory.load(new StringReader(input.text))
    } else if ((input instanceof InputStream) || (input instanceof Reader)) {
      memory.load(input)
    } else {
      memory.load(new StringReader(input.toString()))
    }

    return null
  }
}
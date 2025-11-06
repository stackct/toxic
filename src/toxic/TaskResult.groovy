
package toxic
/**
 * Contains the metrics of the task's output.
 */
class TaskResult implements Serializable {
  static areAllSuccessful(List<TaskResult> results) {
    return getFailedTask(results) == null
  }

  static countFailures(List<TaskResult> results) {
    return results.findAll { !it.success }.size()
  }

  static getFailedTask(List<TaskResult> results) {
    return results.find { !it.success }
  }

  static shouldAbort(def props, List<TaskResult> results) {
    if (props) {
      (props.tmHaltOnError == 'true' && !areAllSuccessful(results)) ||
      (props.containsKey('tmHaltOnErrorThreshold') && (props.tmHaltOnErrorThreshold.toInteger() <= countFailures(results)))
    }
  }

  def id
  def family
  def name
  def type
  private String error
  private String errorType
  private boolean success
  private Long startTime
  private Long stopTime
  private List<String> messages  = new ArrayList<>()

  TaskResult(def id, def family, def name, def type) {
    startTime = System.currentTimeMillis()
    this.id = id
    this.family = family
    this.name = name
    this.type = type
    this.messages
  }

  TaskResult(Map map) {
    fromSimple(map)
  }

  protected void mark() {
    stopTime = System.currentTimeMillis()
  }

  void setSuccess(boolean result) {
    mark()
    this.success = result
  }

  boolean getSuccess() {
    return success
  }

  boolean isComplete() {
    this.stopTime != null
  }

  int getDuration() {
    Math.abs((this.startTime ?: 0) - (this.stopTime ?: 0))
  }

  void setError(String t) {
    mark()
    error = t
  }

  String getSuite() {
    this.family.replaceAll("/", "--").toLowerCase()
  }

  String getError() {
    return error
  }

  def addMessage(String message){
    this.messages.add(message)
  }
  
  def addMessages(List<String> messages){
    this.messages.addAll((java.util.Collection)messages)
  }

  def getMessages(){
    return messages
  }

  long getElapsedTimeMillis() {
    return startTime ? (stopTime ? stopTime - startTime : System.currentTimeMillis() - startTime) : 0
  }

  @Override
  String toString() {
    "TaskResult(id=${id}, family=${family}, name=${name}, type=${type}, error=${error}, success=${success}, startTime=${startTime}, stopTime=${stopTime})"
  }

  Map toSimple() {
    def map        = [:]
    map.id         = this.id
    map.family     = this.family
    map.suite      = this.suite
    map.name       = this.name
    map.type       = this.type
    map.success    = this.success
    map.error      = this.error
    map.startTime  = this.startTime
    map.stopTime   = this.stopTime
    map.complete   = this.complete
    map.duration   = this.elapsedTimeMillis
    map
  }

  def fromSimple(def map) {
    this.id = map.id
    this.family = map.family
    this.name = map.name
    this.type = map.type
    this.error = map.error
    this.success = map.success
    this.startTime = map.startTime
    this.stopTime = map.stopTime
    if(map.messages){
      addMessages(map.messages)
    }
  }
}

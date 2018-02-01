
package toxic

import groovy.time.Duration

/**
 * Contains the metrics of the task's output.
 */
public class TaskResult implements Serializable {
  public static areAllSuccessful(List<TaskResult> results) {
    return results.find { !it.success } == null
  }

  public static countFailures(List<TaskResult> results) {
    return results.findAll { !it.success }.size()
  }

  public static shouldAbort(def props, List<TaskResult> results) {
    (props.tmHaltOnError == 'true' && !areAllSuccessful(results)) ||
    (props.containsKey('tmHaltOnErrorThreshold') && (props.tmHaltOnErrorThreshold.toInteger() <= countFailures(results)))
  }

  public def id
  public def family
  public def name
  public def type
  private String error
  private boolean success
  private Long startTime
  private Long stopTime

  public TaskResult(def id, def family, def name, def type) {
    startTime = System.currentTimeMillis()
    this.id = id
    this.family = family
    this.name = name
    this.type = type
  }
  
  public TaskResult(Map map) {
    fromSimple(map)
  }

  protected void mark() {
    stopTime = System.currentTimeMillis()
  }

  public void setSuccess(boolean result) {
    mark()
    this.success = result
  }

  public boolean getSuccess() {
    return success
  }

  public boolean isComplete() {
    this.stopTime != null
  }

  public int getDuration() {
    Math.abs((this.startTime ?: 0) - (this.stopTime ?: 0))
  }

  public void setError(String t) {
    mark()
    error = t
  }

  public String getSuite() {
    this.family.replaceAll("/", "--").toLowerCase()
  }

  public String getError() {
    return error
  }

  public long getElapsedTimeMillis() {
    return startTime ? (stopTime ? stopTime - startTime : System.currentTimeMillis() - startTime) : 0
  }
  
  @Override
  public String toString() {
    "TaskResult(id=${id}, family=${family}, name=${name}, type=${type}, error=${error}, success=${success}, startTime=${startTime}, stopTime=${stopTime})"
  }

  public Map toSimple() {
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
  
  public def fromSimple(def map) {
    this.id = map.id
    this.family = map.family
    this.name = map.name
    this.type = map.type
    this.error = map.error
    this.success = map.success
    this.startTime = map.startTime
    this.stopTime = map.stopTime
  }
}
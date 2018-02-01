package toxic.job

import groovy.json.*

@Singleton
public class EventManager {
  static final pendingSuffix = ".pending"

  def eventDirectory
  
  def init(eventDir) {
    if (!eventDir) throw new NullPointerException("Invalid event directory")
    eventDirectory = new File(eventDir)
    eventDirectory.mkdirs()
  }
  
  boolean isInitialized() {
    return eventDirectory?.isDirectory()
  }
  
  /**
   * This only needs to be invoked if you want to wipe out all events.
   */
  def destroy() {
    if (isInitialized()) {
      eventDirectory.deleteDir()
    }
    eventDirectory = null
  }

  def readEvent(file) {
    return new JsonSlurper().parse(file)
  }
  
  synchronized def findEvent(String name) {
    def file = new File(eventDirectory, name)
    def event
    if (file.isFile()) {
      event = readEvent(file)
    }
    return event
  }
  
   synchronized void createEvent(String name, def data = [:]) {
    if (!eventDirectory) throw new IllegalStateException("EventManager has not been initialized")
    data.time = now.time
    new File(eventDirectory, name).text = JsonOutput.toJson(data)
  }

  // Events can be created in a pending state until released at a later time. These act like reminders
  // for chained event tasks to later wake up additional tasks.
  synchronized void prepareEvent(String name, def data = [:]) {
    createEvent(name + pendingSuffix, data)
  }

  synchronized void releaseEvents(def pattern, def createdThroughDate) {
    eventDirectory?.eachFile { file ->
      if (file.name.endsWith(pendingSuffix)) {
        def event = readEvent(file)
        if (event.time <= createdThroughDate.time) {
          def name = file.name[0..-(pendingSuffix.size()+1)]
          if (name ==~ pattern) {
            file.renameTo(new File(eventDirectory, name))
          }
        }
      }
    }
  }
  
  def getNow() { return new Date() }
}
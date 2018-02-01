package toxic

import log.Log
import groovy.io.FileType
import groovy.json.JsonOutput

class TaskResultsPublisher {
  private final static Log log = Log.getLogger(this)
  private static int defaultConnectionTimeout = 5000
  private static int defaultReadTimeout = 5000
  def props
  Thread t
  def taskMasterResultIndex = [:]
  boolean running = false

  TaskResultsPublisher(def props) {
    this.props = props
  }

  void startPublishingTaskMasterResultsAsynchronously(def taskMasters) {
    running = true
    t = new Thread() {
      public void run() {
        while(running) {
          publishTaskMasterResults(taskMasters)
        }
        publishTaskMasterResults(taskMasters)
      }
    }
    t.start()
  }

  void publishTaskMasterResults(def taskMasters) {
    def results = []
    TaskMaster.walkResults(taskMasters, taskMasterResultIndex) { r ->
      results << r.toSimple()
    }

    if(results) {
      def failureHandler = { String message ->
        log.error("Failed to publish task master results; reason=${message}; results=${results}")
      }
      send([method: 'POST', contentType: 'application/json', url: props.taskResultsPublisherUrl, body: JsonOutput.toJson(results), successHandler: {}, failureHandler: failureHandler])
    }
  }

  void publishArtifactDirectory(File artifactsDir, String baseUrl, String jobId) {
    artifactsDir.eachFileRecurse(FileType.FILES) { file ->
      String url = "${baseUrl}/api/job/${jobId}/publish/artifact/${file.name}"
      publishArtifact(url, file)
    }
  }

  void publishArtifact(String url, File artifact) {
    def failureHandler = { String message ->
      log.error("Failed to publish artifact; reason=${message}; artifact=${artifact}")
    }
    send([method: 'POST', contentType: 'application/octet-stream', url: url, body: artifact, successHandler: {}, failureHandler: failureHandler])
  }

  void send(Map request) {
    HttpURLConnection conn
    try {
      conn = new URL(request.url).openConnection()
      conn.setConnectTimeout(props.taskResultsPublisherConnectionTimeout ?: defaultConnectionTimeout)
      conn.setReadTimeout(props.taskResultsPublisherReadTimeout ?: defaultReadTimeout)
      conn.setRequestMethod(request.method)
      conn.setRequestProperty("Content-Type", request.contentType)
      conn.setDoOutput(true)

      OutputStream os = conn.getOutputStream()
      os.write(request.body.getBytes())
      os.flush()

      if(conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
        request.successHandler()
      }
      else {
        request.failureHandler("HTTP Response ${conn.getResponseCode()}")
      }
    }
    catch(IOException e) {
      request.failureHandler(e.message)
    }
    finally {
      conn?.disconnect()
    }
  }

  void blockingStop() {
    nonblockingStop()
    t.join()
  }

  void nonblockingStop() {
    running = false
  }
}

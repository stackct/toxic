package toxic

import groovy.io.FileType
import groovy.json.JsonOutput
import org.apache.log4j.Level
import org.junit.After
import org.junit.Test

class TaskResultsPublisherTest {
  @After
  void after() {
    URL.metaClass = null
  }

  @Test
  void should_construct() {
    def taskResultsPublisher = new TaskResultsPublisher(['key1':'value1', 'key2':'value2'])
    assert ['key1':'value1', 'key2':'value2'] == taskResultsPublisher.props
    assert !taskResultsPublisher.running
  }

  @Test
  void should_start_and_stop_processing_with_block() {
    def taskResultsPublisher = new TaskResultsPublisher([:])
    taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously([])
    assert taskResultsPublisher.running
    assert taskResultsPublisher.t.isAlive()
    taskResultsPublisher.blockingStop()
    assert !taskResultsPublisher.running
    assert !taskResultsPublisher.t.isAlive()
  }

  @Test
  // original name: should_publish_results_after_running_to_guarantee_no_results_were_added_during_the_time_spent_sending_results
  void should_publish_results() {
    def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001'])
    def blockAsyncThread = true
    def actualRequests = []
    taskResultsPublisher.metaClass.send = { Map request ->
      actualRequests << request
      while(blockAsyncThread) { sleep(100) }
    }

    def taskResult1 = new TaskResult(id:'1.1', family: 'test')
    def taskMasters = []
    taskMasters << new TaskMaster(id: '1', results: [taskResult1])
    taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously(taskMasters)

    // Block until taskResult1 is processed
    while(!actualRequests) { sleep(100) }

    // Add a result to the task master while async thread is hung publishing taskResult1
    def taskResult2 = new TaskResult(id:'2.1', family: 'test')
    taskMasters[0].results << taskResult2

    // Stop publisher thread in a non-blocking manor and unblock the async thread
    taskResultsPublisher.nonblockingStop()
    blockAsyncThread = false

    // Block until the publisher thread completes and verify taskResult2 was published
    taskResultsPublisher.blockingStop()
    assert 2 == actualRequests.size()
  }

  @Test
  void should_use_default_connection_timeouts_when_timeouts_are_not_configured() {
    mockSuccessHttpResponse { def request ->
      def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001'])
      taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously([new TaskMaster(results:[new TaskResult(id:'1.1', family: 'test')])])
      taskResultsPublisher.blockingStop()
      assert 5000 == request.connectTimeout
      assert 5000 == request.readTimeout
    }
  }

  @Test
  void should_configure_connection_timeout_from_properties() {
    mockSuccessHttpResponse { def request ->
      def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001', taskResultsPublisherConnectionTimeout:1000, taskResultsPublisherReadTimeout:2000])
      taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously([new TaskMaster(results:[new TaskResult(id:'1.1', family: 'test')])])
      taskResultsPublisher.blockingStop()
      assert 1000 == request.connectTimeout
      assert 2000 == request.readTimeout
    }
  }

  @Test
  void should_publish_task_results_successfully() {
    mockSuccessHttpResponse { def request ->
      def taskResult1 = new TaskResult(id:'1.1', family: 'test')
      def taskResult2 = new TaskResult(id:'2.1', family: 'test')
      def taskResult3 = new TaskResult(id:'3.1', family: 'test')
      def taskMasters = []
      taskMasters << new TaskMaster(id: '1', results: [taskResult1, taskResult2, taskResult3])

      def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001'])
      taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously(taskMasters)
      taskResultsPublisher.blockingStop()

      assert 'POST' == request.requestMethod
      assert 'application/json' == request.'Content-Type'
      assert 'http://localhost:8001' == request.url
      assert JsonOutput.toJson([taskResult1.toSimple(), taskResult2.toSimple(), taskResult3.toSimple()]) == new String(request.bytes)
    }
  }

  @Test
  void should_handle_non_success_http_response_when_task_results_fail_to_publish() {
    TaskResultsPublisher.log.track { logger ->
      mockFailureHttpResponse { def request ->
        def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001'])
        taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously([new TaskMaster(results:[new TaskResult(id:'1.1', family: 'test')])])
        taskResultsPublisher.blockingStop()
        assert logger.isLogged('HTTP Response 500', Level.ERROR)
      }
    }
  }

  @Test
  void should_handle_ioexception_when_task_results_fail_to_publish() {
    TaskResultsPublisher.log.track { logger ->
      mockFailureWriteIoFailure { def request ->
        def taskResultsPublisher = new TaskResultsPublisher([taskResultsPublisherUrl:'http://localhost:8001'])
        taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously([new TaskMaster(results:[new TaskResult(id:'1.1', family: 'test')])])
        taskResultsPublisher.blockingStop()
        assert logger.isLogged('Failed to write request', Level.ERROR)
      }
    }
  }

  @Test
  void should_publish_artifact_successfully() {
    mockSuccessHttpResponse { def request ->
      String filePath = '/tmp/results.tar.gz'
      def file = new File(filePath)
      file.metaClass.getBytes = {
        filePath.getBytes()
      }
      def taskResultsPublisher = new TaskResultsPublisher([:])
      taskResultsPublisher.publishArtifact('http://localhost:8001', file)
      assert 'POST' == request.requestMethod
      assert 'application/octet-stream' == request.'Content-Type'
      assert 'http://localhost:8001' == request.url
      assert filePath == new String(request.bytes)
    }
  }

  @Test
  void should_publish_artifact_directory() {
    mockSuccessHttpResponse { def request ->
      String filePath = '/tmp/results.tar.gz'
      def file = new File(filePath)
      file.metaClass.eachFileRecurse = {FileType fileType, Closure closure ->
        closure(file)
      }
      file.metaClass.getBytes = {
        filePath.getBytes()
      }
      def taskResultsPublisher = new TaskResultsPublisher([:])
      taskResultsPublisher.publishArtifactDirectory(file, 'http://localhost:8001', '12345')
      assert 'POST' == request.requestMethod
      assert 'application/octet-stream' == request.'Content-Type'
      assert 'http://localhost:8001/api/job/12345/publish/artifact/results.tar.gz' == request.url
      assert filePath == new String(request.bytes)
    }
  }

  @Test
  void should_handle_non_success_http_response_when_artifact_fails_to_publish() {
    TaskResultsPublisher.log.track { logger ->
      String filePath = '/tmp/results.tar.gz'
      def file = new File(filePath)
      file.metaClass.getBytes = {
        filePath.getBytes()
      }
      mockFailureHttpResponse { def request ->
        def taskResultsPublisher = new TaskResultsPublisher([:])
        taskResultsPublisher.publishArtifact('http://localhost:8001', file)
        assert logger.isLogged('Failed to publish artifact; reason=HTTP Response 500; artifact=/tmp/results.tar.gz', Level.ERROR)
      }
    }
  }

  void mockSuccessHttpResponse(Closure closure) {
    mockHttpResponse([httpOk:true], closure)
  }

  void mockFailureHttpResponse(Closure closure) {
    mockHttpResponse([:], closure)
  }

  void mockFailureWriteIoFailure(Closure closure) {
    mockHttpResponse([writeIoException:true], closure)
  }

  void mockHttpResponse(def handlers, Closure closure) {
    def request = [:]
    URL.metaClass.openConnection = {
      request.url = delegate.toString()
      new HttpURLConnection(new URL(request.url)) {
        void disconnect() {}
        boolean usingProxy() { false }
        void connect() throws IOException { }
        void setConnectTimeout(int timeout) { request.connectTimeout = timeout }
        void setReadTimeout(int timeout) { request.readTimeout = timeout }
        void setRequestMethod(String method) { request.requestMethod = method }
        void setRequestProperty(String key, String value) { request."${key}" = value }
        void setDoOutput(boolean dooutput){ request.dooutput = dooutput}
        OutputStream getOutputStream() {
          if(handlers.writeIoException) {
            throw new IOException('Failed to write request')
          }
          [write:{byte[] bytes -> request.bytes = bytes}, flush:{request.flushed=true}] as OutputStream}
        int getResponseCode() { handlers.httpOk ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_INTERNAL_ERROR }
      }
    }
    closure(request)
  }
}

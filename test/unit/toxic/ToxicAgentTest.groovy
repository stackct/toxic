// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import groovy.mock.interceptor.MockFor
import org.junit.*

public class ToxicAgentTest {
  @Test
  void should_construct() {
    ToxicAgent toxicAgent = new ToxicAgent()
    assert toxicAgent.remoteTaskMaster
  }

  @Test
  public void testInitCallShutdown() {
    def ta = new ToxicAgent()
    def props = new ToxicProperties()
    props.agentTaskMasterCount="1"
    props.tmReps="0"
    ta.init(props)
    assert props.fastClassMap instanceof Map
    ta.call()
    ta.shutdown()
    assert ta.props.shutdownInProgress
    assert ta.isShutdown()
    assert ta.loadedTrustStore
  }
  
  @Test
  public void test_call_formatter_empty_classname() {
    def cp = []
    def ta = new ToxicAgent() 
    def props = new ToxicProperties()
    props.foo = 1
    ta.init(props)

    // should not throw exception on an empty class
    ta.callFormatter(null, []) 

    ta.callFormatter("agent.formatter.1", []) 
    
    ta.props["agent.formatter.1"] = null
    ta.callFormatter("agent.formatter.1", []) 

    ta.props["agent.formatter.1"] = " "
    ta.callFormatter("agent.formatter.1", []) 
  }
  
  @Test
  public void should_construct_daemon_threads() {
    def ta = new ToxicAgent()
    def tf = ta.newThreadFactory()
    def testThread = tf.newThread(new Runnable() { void run() { }})
    assert testThread.daemon
  }

  @Test
  void should_add_remote_result() {
    def toxicAgent = new ToxicAgent()
    assert [] == toxicAgent.remoteTaskMaster.results
    toxicAgent.addRemoteResult(new TaskResult(id:1))
    assert 1 == toxicAgent.remoteTaskMaster.results.size()
    assert 1 == toxicAgent.remoteTaskMaster.results[0].id
  }

  @Test
  void should_create_task_master_for_handling_remote_results() {
    def ta = new ToxicAgent()
    def props = new ToxicProperties()
    props.agentTaskMasterCount="1"
    props.tmReps="0"
    ta.init(props)
    assert [] == ta.taskMasters
    ta.call()
    assert 2 == ta.taskMasters.size()
  }

  @Test
  void should_not_publish_results_to_remote_server_when_property_is_not_configured() {
    def ta = new ToxicAgent()
    def props = new ToxicProperties()
    props.agentTaskMasterCount="1"
    props.tmReps="0"
    ta.init(props)

    def mockPublisher = new MockFor(TaskResultsPublisher)
    mockPublisher.demand.startPublishingTaskMasterResultsAsynchronously(0) { def taskMasters -> }
    mockPublisher.demand.blockingStop(0) {}
    mockPublisher.use {
      ta.call()
    }
  }

  @Test
  void should_configure_environment_on_init() {
    File tempDir
    try {
      tempDir = File.createTempDir()
      def ta = new ToxicAgent()
      def props = new ToxicProperties()
      def artifactsDirectory = new File(tempDir, 'artifacts')
      props.artifactsDirectory=artifactsDirectory.absolutePath
      ta.init(props)
      ta.call()
      assert artifactsDirectory.isDirectory()
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_publish_results_to_remote_server_when_property_is_configured() {
    def ta = new ToxicAgent()
    def props = new ToxicProperties()
    props.agentTaskMasterCount="1"
    props.tmReps="0"
    props.taskResultsPublisherUrl = 'http://localhost'
    ta.init(props)

    def mockPublisher = new MockFor(TaskResultsPublisher, true)
    def taskResultsPublisher = new TaskResultsPublisher()
    def actualProps = null
    def actualTaskMasters = null
    mockPublisher.demand.with {
      TaskResultsPublisher() { def p -> actualProps = p; taskResultsPublisher }
    }
    mockPublisher.demand.startPublishingTaskMasterResultsAsynchronously(1) { def taskMasters ->
      actualTaskMasters = taskMasters
      ta.remoteTaskMaster.results << new TaskResult(id:'12345', family:'test')
    }
    mockPublisher.demand.blockingStop(1) {}
    mockPublisher.use {
      def results = ta.call()
      assert 2 == results.size()
      assert '12345' == results[1].id
      assert 'test' == results[1].family
      assert props.taskResultsPublisherUrl == actualProps.taskResultsPublisherUrl
      assert ta.taskMasters == actualTaskMasters
    }
  }
}

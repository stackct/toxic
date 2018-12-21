
package toxic

import java.security.*
import javax.net.ssl.*
import java.util.concurrent.*
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * The ToxicAgent is responsible for spawning one or more TaskMasters,
 * as configured in the toxic configuration properties.  Once started,
 * the ToxicAgent will wait until each TaskMaster has finished
 * before shutting down and exiting.
 */
public class ToxicAgent implements Callable<List<TaskResult>> {
  protected static Logger slog = Logger.getLogger(ToxicAgent.class.name)
  private static boolean loadedTrustStore = false
  private def props
  private ExecutorService pool
  private def taskMasters = []
  private boolean shutdown = false
  private def tmIndex = 0
  private def remoteTaskMaster = new TaskMaster()

  protected Logger getLog() {
    return props?.log ?: this.slog
  }

  /**
   * Initializes with a set of property key/value pairs.
   *
   * See the deault toxic.properties file for descriptions of the supported
   * properties.
   */
  public void init(ToxicProperties props) {
    if (!props) {
      throw new IllegalArgumentException("Configuration properties not specified")
    }
    this.props = new ToxicProperties(props)
    
    // Initialize the cache to be shared across all taskmasters spawned by this agent
    this.props.fastClassMap = [:]

    if (log.isDebugEnabled()) {
      log.debug("Initialized agent with props=" + props.toString())
    }

    loadTrustStore()
    configureEnvironment()
  }

  def loadTrustStore() {
    if (!loadedTrustStore) {
      synchronized(loadedTrustStore) {
        if (!loadedTrustStore) {
          def multiTm = new MultiX509TrustManager()
          if (props.validateCerts.toString().toLowerCase() == "false") {
            multiTm.setTrustRequired(false)
          } else {
            multiTm.setTrustRequired(true)
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(ToxicAgent.class.getClassLoader().getResourceAsStream("toxic.jks"), null);
            TrustManagerFactory customFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            customFactory.init(trustStore);
            def customManagers = customFactory.getTrustManagers() as List

            TrustManagerFactory defaultFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaultFactory.init((KeyStore)null);
            def defaultManagers = defaultFactory.getTrustManagers() as List
          
            (customManagers + defaultManagers).each {
              multiTm.addTrustManager(it)
            }
          }
          
          def trustManagers = new TrustManager[1]
          trustManagers[0] = multiTm

          SSLContext sslContext = SSLContext.getInstance("SSL");
          sslContext.init(null, trustManagers, null);
          SSLContext.setDefault(sslContext);

          loadedTrustStore = true
        }
      }
    }
  }

  void configureEnvironment() {
    if(props.artifactsDirectory) {
      new File(props.artifactsDirectory).mkdirs()
    }
  }
  
  def newThreadFactory() {
    def threadFactory = new ThreadFactory() {
      Thread newThread(Runnable r) {
        def thread = new Thread(r, "taskmaster-${tmIndex++}")
        thread.daemon = true
        return thread
      }
    }
    return threadFactory    
  }

  /**
   * This thread will block until the agent and its task masters complete.
   * This function should only be invoked once.  Do not rerun the same ToxicAgent
   * multiple times.
   */
  public List<TaskResult> call() {
    if (this.shutdown) {
      throw new IllegalStateException("Agent has already shutdown")
    }

    if (!this.props) {
      throw new IllegalStateException("Cannot start agent until agent is initialized")
    }

    log.debug("Starting agent")
    List<TaskResult> results = new ArrayList<TaskResult>()
    def startTime = System.currentTimeMillis()
    def futures = []

    try {
      synchronized (this) {
        this.pool = Executors.newFixedThreadPool(Integer.parseInt(props.agentTaskMasterCount), newThreadFactory())
        Long.parseLong(props.agentTaskMasterCount).times() {
          // Initialize a TaskMaster
          def tm = new TaskMaster()
          tm.init(this.props, it)
          this.taskMasters << tm
          futures << this.pool.submit(tm)
        }
        // This task master is used by the Toxic Server process so remote task results can be appended to an isolated task master to avoid concurrency issues with Job.localSimpleResults
        // This task master will also be created on a child toxic process, but no results should be appended
        this.taskMasters << remoteTaskMaster
      }

      TaskResultsPublisher taskResultsPublisher = null
      //This property is expected to only be defined on a child toxic process for publishing results to a remote toxic ui server
      if(props.taskResultsPublisherUrl) {
        taskResultsPublisher = new TaskResultsPublisher(props)
        taskResultsPublisher.startPublishingTaskMasterResultsAsynchronously(taskMasters)
      }

      def finishedFutures = []
      while (finishedFutures.size() < futures.size()) {
        futures.each {
          if (!finishedFutures.contains(it)) {
            try {
              results.addAll(it.get(20, TimeUnit.MILLISECONDS))
              finishedFutures << it
            } catch (TimeoutException to) {
            }
          }
        }
      }

      taskResultsPublisher?.blockingStop()
      results.addAll(remoteTaskMaster.results)
      formatResults(results)
      } catch (Exception e) {
      log.error("caught exception ${e}", e)
    } finally {
      this.shutdown = true

      this.pool?.shutdownNow()

      def elapsedTime = System.currentTimeMillis() - startTime
      def success = 0
      results.each {
        success += (it.success ? 1 : 0)
      }
      log.info("Agent Shutdown; durationMS=" + elapsedTime + "; tasks=" + results.size() + "; success=" + success + "; fail=" + (results.size() - success))
    }
    return results
  }

  protected void addRemoteResult(TaskResult taskResult) {
    remoteTaskMaster.results << taskResult
  }

  protected callFormatter(def classProperty, List<TaskResult> results) {
    if (!props[classProperty] || !props[classProperty].trim()) return
    try {
      def c = props.resolveClass(classProperty)
      if (c == null) {
        throw new IllegalArgumentException("Missing classname for property; classProperty=" + classProperty)
      }
      def formatter = c.newInstance()
      formatter.init(props)
      formatter.format(results)
    } catch (Exception e) {
      log.error("Failed to invoke TaskResultsFormatter; formatterName=" + classProperty + "; resultCount=" + results?.size())
      throw e
    }
  }

  protected void formatResults(List<TaskResult> results) {
    props.keySet().sort().each { key ->
      if (key.startsWith("agent.formatter.")) {
        callFormatter(key, results)
      }
    }
  }

  /**
   * Notifies all the TaskMasters spawned by this agent to stop
   * processing and exit. Do not re-use this ToxicAgent once it has been
   * shutdown.
   */
  public synchronized shutdown() {
    log.debug("Attempting to shutdown task masters; taskMasterCount=" + this.taskMasters.size())
    this.props?.shutdownInProgress = true
    this.taskMasters.each {
      if (!it.isShutdown()) {
        it.shutdown()
      }
    }
  }

  /**
   * Returns true if this agent is still processing.
   */
  public boolean isShutdown() {
    return this.shutdown
  }
}

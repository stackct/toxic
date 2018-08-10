package toxic

import java.lang.management.*
import org.apache.log4j.Logger

@Singleton(strict=false)
class Environment {

  private static Logger log = Logger.getLogger(Environment.class.name)

  static long statsRefreshInterval

  static reset() {
    statsRefreshInterval = 60000 // yeah, it seems long but these are very expensive stats to collect
  }

  static { 
    reset()
  }

  def opBean
  def memBean
  def lastCheckTime = 0
  def simpleStats

  private Environment() {
    opBean  = ManagementFactory.getOperatingSystemMXBean();
    memBean = ManagementFactory.getMemoryMXBean();
  }

  public double getLoadAverage() {
    return opBean.systemLoadAverage
  }

  public synchronized Map toSimple() {
    def now = new Date().time
    if (now - lastCheckTime <= statsRefreshInterval) return simpleStats

    def heap = memBean.heapMemoryUsage

    def map = [:]
    map.appVersion  = Toxic.version
    map.os          = opBean.name
    map.version     = opBean.version
    map.arch        = opBean.arch
    map.load        = loadAverage
    map.procs       = opBean.availableProcessors
    map.heapUsed    = heap.used
    map.heapMax     = heap.max
    map.docker      = dockerInfo

    simpleStats = map
    lastCheckTime = now

    map
  }

  public Map getDockerInfo() {
    def info = [:]
    def procResult = exec("docker info")

    if (procResult.exitValue == 0) {

      def matchers = [:]
      matchers['running'] = /(?m)^\s?Running:\s*(.*)$/
      matchers['paused']  = /(?m)^\s?Paused:\s*(.*)$/
      matchers['stopped'] = /(?m)^\s?Stopped:\s*(.*)$/
      matchers['images']  = /(?m)^\s?Images:\s*(.*)$/
      matchers['serverVersion']  = /(?m)^\s?Server Version:\s*(.*)$/
      matchers['storageDriver']  = /(?m)^\s?Storage Driver:\s*(.*)$/
      matchers['dataSpaceUsed']  = /(?m)^\s?Data Space Used:\s*(.*)$/
      matchers['dataSpaceTotal']  = /(?m)^\s?Data Space Total:\s*(.*)$/
      matchers['dataSpaceAvailable']  = /(?m)^\s?Data Space Available:\s*(.*)$/

      def collector = [:]

      procResult.output.toString().eachLine { line ->
        matchers.each { key, pattern ->
          parseLine(line, pattern, collector)
        }
      }

      collector.each { pattern, value -> 
        matchers.find { k,v -> v == pattern }. with { entry ->
          info[entry.key] = value
        }
      }
    }

    return info
  }

  private def parseLine(line, pattern, collector) {
    (line =~ pattern).with { m ->
      if (m.matches()) collector[pattern] = m[0][1]
    }
  }

  public String generateThreadDump() {
    def result = new StringBuilder()
    def bean = ManagementFactory.getThreadMXBean()
    def threads = bean.getThreadInfo(bean.getAllThreadIds(), 75)
    threads.each {
      if (it != null) {
        result.append("\"${it.threadName}\"\n")
        result.append("   java.lang.Thread.State: ${it.threadState}\n")
        it.stackTrace.each { method ->
          result.append("        at ${method}\n");
        }
        result.append("\n")
      }
    }
    return result.toString();
  }

  protected Map exec(String cmd) {
    def stdout = new StringBuffer()
    def stderr = new StringBuffer()
    def proc
    synchronized(this) {
      proc = Runtime.runtime.exec(cmd)
      proc.waitForProcessOutput(stdout, stderr)
    }

    if (!proc.exitValue() == 0) {
      log.warn("Could not retrieve Docker information; stdout=${stdout} stderr=${stderr}")
    }

    [output:stdout, error:stderr, exitValue: proc.exitValue()]
  }

  @Override
  public String toString() {
    def heap = memBean.heapMemoryUsage
    return "os=${opBean.name}; version=${opBean.version}; arch=${opBean.arch}; load=${loadAverage}; procs=${opBean.availableProcessors}; heapUsed=${heap.used}; heapMax=${heap.max}"
  }
}

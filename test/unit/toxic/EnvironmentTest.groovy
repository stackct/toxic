package toxic

import org.junit.*

import java.lang.management.*

class EnvironmentTest {

  @Before
  public void before() {
    ManagementFactory.metaClass = null
  }

  @After
  public void after() {
    Toxic.metaClass = null
    Runtime.metaClass = null
    Environment.reset()
  }

  @Test
  public void should_construct_singleton() {
    assert Environment.instance == Environment.instance
  }

  @Test
  public void should_serialize_to_simple_map() {

    ManagementFactory.metaClass.'static'.getOperatingSystemMXBean = { -> 
      [name:'fooOS', version:'1.0', arch:'xFooBar', availableProcessors:99, systemLoadAverage: 2.5]
    }
    
    ManagementFactory.metaClass.'static'.getMemoryMXBean = { -> 
      [heapMemoryUsage: [used: 12345, max:99999], nonHeapMemoryUsage: [used:33, max:1000]]
    }

    Toxic.metaClass.'static'.getVersion = { -> '9.9.9.9' }

    def execOut = new StringBuilder() 

    execOut.append("Containers: 12" + '\n')
    execOut.append(" Running: 8" + '\n')
    execOut.append(" Paused: 3" + '\n')
    execOut.append(" Stopped: 1" + '\n')
    execOut.append("Images: 216" + '\n')
    execOut.append("Server Version: 1.13.1" + '\n')
    execOut.append("Storage Driver: aufs" + '\n')
    execOut.append(" Root Dir: /var/lib/docker/aufs" + '\n')
    execOut.append(" Backing Filesystem: extfs" + '\n')
    execOut.append(" Dirs: 598" + '\n')
    execOut.append(" Dirperm1 Supported: true" + '\n')

    Runtime.metaClass.exec = { String cmd -> mockProcess(execOut.toString(), null, 0) }

    new Environment().toSimple().with { env ->
      assert env.appVersion        == '9.9.9.9'
      assert env.os                == 'fooOS'
      assert env.version           == '1.0'
      assert env.arch              == 'xFooBar'
      assert env.load              == 2.5
      assert env.procs             == 99
      assert env.heapUsed          == 12345
      assert env.heapMax           == 99999
      assert env.docker.running    == '8'
      assert env.docker.paused     == '3'
      assert env.docker.stopped    == '1'
      assert env.docker.serverVersion == '1.13.1'
    }
  }

  @Test
  public void should_retrieve_docker_information() {
    def execOut = new StringBuilder() 

    execOut.append("Containers: 12" + '\n')
    execOut.append(" Running: 8" + '\n')
    execOut.append(" Paused: 3" + '\n')
    execOut.append(" Stopped: 1" + '\n')
    execOut.append("Images: 216" + '\n')
    execOut.append("Server Version: 1.13.1" + '\n')
    execOut.append("Storage Driver: devicemapper" + '\n')
    execOut.append(" Pool Name: docker-8:16-58195972-pool" + '\n')
    execOut.append(" Pool Blocksize: 65.54 kB" + '\n')
    execOut.append(" Base Device Size: 10.74 GB" + '\n')
    execOut.append(" Backing Filesystem: xfs" + '\n')
    execOut.append(" Data file: /dev/loop0" + '\n')
    execOut.append(" Metadata file: /dev/loop1" + '\n')
    execOut.append(" Data Space Used: 81.86 GB" + '\n')
    execOut.append(" Data Space Total: 107.4 GB" + '\n')
    execOut.append(" Data Space Available: 25.52 GB" + '\n')
    execOut.append(" Metadata Space Used: 117.5 MB" + '\n')
    execOut.append(" Metadata Space Total: 2.147 GB" + '\n')
    execOut.append(" Metadata Space Available: 2.03 GB" + '\n')
    execOut.append(" Thin Pool Minimum Free Space: 10.74 GB" + '\n')

    Runtime.metaClass.exec = { String cmd -> mockProcess(execOut.toString(), null, 0) }

    def info = new Environment().getDockerInfo()

    assert info.running == '8'
    assert info.paused  == '3'
    assert info.stopped == '1'
    assert info.images  == '216'
    assert info.serverVersion == '1.13.1'
    assert info.storageDriver == 'devicemapper'
    assert info.dataSpaceUsed == '81.86 GB'
    assert info.dataSpaceTotal == '107.4 GB'
    assert info.dataSpaceAvailable == '25.52 GB'
  }

  private def mockProcess(String stdout, String stderr, int exitValue) {
    def proc = [:]
    proc.exitValue = { -> exitValue }
    proc.waitForProcessOutput = { out, err -> 
      if (stderr) { err.append(stderr) }
      if (stdout) { out.append(stdout) }
    }
    
    return proc
  }

  @Test
  void should_generate_thread_dump() {
    def dump = new Environment().generateThreadDump()
    assert dump.contains("   java.lang.Thread.State: ")
    assert dump.contains("(Environment.groovy")
  }

  @Test
  void should_respect_refresh_interval() {
    Environment.statsRefreshInterval = 3000

    ManagementFactory.metaClass.'static'.getOperatingSystemMXBean = { -> 
      [name:'fooOS', version:'1.0', arch:'xFooBar', availableProcessors:99, systemLoadAverage: 2.5]
    }
    
    ManagementFactory.metaClass.'static'.getMemoryMXBean = { -> 
      [heapMemoryUsage: [used: 12345, max:99999], nonHeapMemoryUsage: [used:33, max:1000]]
    }

    Toxic.metaClass.'static'.getVersion = { -> '9.9.9.9' }

    def execOut = new StringBuilder() 

    int containers = 1
    execOut.append("Containers: ${containers++}" + '\n')
    execOut.append(" Running: 8" + '\n')
    execOut.append(" Paused: 3" + '\n')
    execOut.append(" Stopped: 1" + '\n')
    execOut.append("Images: 216" + '\n')
    execOut.append("Server Version: 1.13.1" + '\n')
    execOut.append("Storage Driver: aufs" + '\n')
    execOut.append(" Root Dir: /var/lib/docker/aufs" + '\n')
    execOut.append(" Backing Filesystem: extfs" + '\n')
    execOut.append(" Dirs: 598" + '\n')
    execOut.append(" Dirperm1 Supported: true" + '\n')

    Runtime.metaClass.exec = { String cmd -> mockProcess(execOut.toString(), null, 0) }

    def map = Environment.instance.toSimple()
    10.times { 
      def map2 = Environment.instance.toSimple()
      assert map == map2
    }
    sleep(5000)
    def map3 = Environment.instance.toSimple()
    assert map != map3
  }
}
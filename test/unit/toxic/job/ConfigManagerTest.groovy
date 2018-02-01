package toxic.job

import org.junit.*
import groovy.json.*
import groovy.mock.interceptor.*

class ConfigManagerTest {
  @Test
  void should_read() {
    def fileMock = new MockFor(File) 
    fileMock.demand.isFile() { true }
    def slurperMock = new MockFor(JsonSlurper)
    slurperMock.demand.parse() { File file -> [p:true] }
    fileMock.use {
      slurperMock.use {
        def map = ConfigManager.instance.read(new JobManager("rl"), "test")
        assert map.p
        assert !map.j
      }
    }
  }   

  @Test
  void should_write() {
    def gotText
    def fileMock = new MockFor(File) 
    fileMock.ignore.isDirectory() { true }
    fileMock.ignore.setText() { String text -> gotText = text}
    fileMock.use {
      ConfigManager.instance.write(new JobManager("rl"), "blk", [p:true])
      assert gotText == '{"p":true}'
    }
  }
}
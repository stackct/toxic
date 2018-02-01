
package toxic

import org.junit.*
import groovy.mock.interceptor.*

public class ToxicServerTest {

  def server

  @Before
  void before() {
    server = new ToxicServer([:])
  }

  @Test
  void should_run_configured_services() {
    def servicesRun = []

    server.services = [ 
      { -> servicesRun << "foo" }, 
      { -> servicesRun << "bar" } 
    ]

    server.run()

    assert servicesRun == ['foo', 'bar']
  }

  @Test
  void should_determine_server_uptime() {
    assert server.upTime == '0s'

    new MockFor(Date).with { mock ->
      mock.demand.getTime { 40 }
      mock.use {
        server.run()
      }
    }

    new MockFor(Date).with { mock ->
      mock.demand.getTime { 50000 }
      mock.use {
        assert server.upTime == '49s'
      }
    }
  }
}

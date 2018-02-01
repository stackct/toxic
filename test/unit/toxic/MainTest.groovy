package toxic

import org.junit.*

public class MainTest {
  @After
  void after() {
    Main.metaClass = null
  }

  @Test
  void should_configure_factory_default_logging() {
    ToxicProperties props = new ToxicProperties()
    def defaulted
    Main.metaClass.'static'.configureDefaultLogging = { -> defaulted=true}
    props.logConf = ""
    Main.configureLogging(props)
    assert defaulted
  }  

  @Test
  void should_configure_standard_logging() {
    ToxicProperties props = new ToxicProperties()
    def log4jResource
    Main.metaClass.'static'.configureCustomLogging = { ToxicProperties props2 -> log4jResource = props.logConf }
    Main.configureLogging(props)
    assert log4jResource == "log4j.xml"
  }  

  @Test
  void should_configure_custom_logging() {
    ToxicProperties props = new ToxicProperties()
    def log4jResource
    Main.metaClass.'static'.configureCustomLogging = { ToxicProperties props2 -> log4jResource = props.logConf }
    props.logConf = "foo.xml"
    Main.configureLogging(props)
    assert log4jResource == "foo.xml"
  }  
}
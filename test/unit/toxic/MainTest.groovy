package toxic

import log.Log
import org.apache.log4j.Level
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
  void should_configure_default_logging() {
    ToxicProperties props = new ToxicProperties()
    def defaulted
    props.useDefaultLogging = "" // just needs the key to exist, value is ignored
    Main.metaClass.'static'.configureDefaultLogging = { defaulted = true }
    Main.configureLogging(props)
    assert defaulted
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

  @Test
  void should_load_parent_properties_from_multiple_do_dirs() {
    def doDirs = []
    Main.metaClass.'static'.loadParentProperties = { ToxicProperties props, def doDir ->
      doDirs << doDir
    }
    Main.loadProperties(['-doDir=/foo', '-doDir1=/bar', '-doDir2=/foobar'] as String[])
    assert ['/foo', '/bar', '/foobar'] == doDirs
  }

  @Test
  void should_not_load_parent_props_when_file_does_not_exist() {
    Log log = Log.getLogger(Main.class)
    log.track { logger ->
      ToxicProperties toxicProperties = new ToxicProperties()
      toxicProperties['log'] = log
      Main.loadPropertiesFile(toxicProperties, new File('/path/does/not/exist'))
      assert !logger.isLogged('Failed to load properties; invalidPropFile=/path/does/not/exist', Level.WARN)
    }
  }

  @Test
  void should_load_properties_from_user_home_when_exists() {
    def files = []
    Main.metaClass.'static'.loadPropertiesFile = { ToxicProperties props, def propFile, boolean useClasspath = false ->
      files << propFile
    }
    Main.loadProperties(['-doDir=/foo'] as String[])

    String classPathProperties = 'toxic.properties'
    File globalProperties = new File(System.getenv()['HOME'], '.toxic/global.properties')
    assert [classPathProperties, globalProperties] == files
  }

  @Test
  void should_reload_properties_from_user_home_when_exists_with_parent_props() {
    def files = []
    Main.metaClass.'static'.loadPropertiesFile = { ToxicProperties props, def propFile, boolean useClasspath = false ->
      files << propFile
    }
    Main.loadProperties(['-doDir=/foo', '-parentProps=true'] as String[])

    String classPathProperties = 'toxic.properties'
    File globalProperties = new File(System.getenv()['HOME'], '.toxic/global.properties')
    assert [classPathProperties, globalProperties, classPathProperties, globalProperties] == files
  }
}
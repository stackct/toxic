package toxic

import org.junit.After
import org.junit.Test

class ToxicTest {
  @After
  void after() {
    Toxic.metaClass = null
    Toxic.reset()
  }

  @Test
  void should_default_version_to_null() {
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('VERSION' == name) {
        return null
      }
    }
    Toxic.loadVersion()
    assert null == Toxic.getVersion()
  }

  @Test
  void should_load_version() {
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('VERSION' == name) {
        return '1.2.3'
      }
    }
    Toxic.loadVersion()
    assert '1.2.3' == Toxic.getVersion()
  }

  @Test
  void should_gen_product_version_string() {
    TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('VERSION' == name) {
        return '1.2.3'
      }
    }
    Toxic.loadVersion()
    assert 'Toxic - Version 1.2.3' == Toxic.genProductVersionString('Toxic')
  }
}

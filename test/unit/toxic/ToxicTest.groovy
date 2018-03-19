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
  void should_default_build_date_to_null() {
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('BUILDDATE' == name) {
        return null
      }
    }
    Toxic.loadVersion()
    assert null == Toxic.getBuildDate()
    assert null == Toxic.getBuildTime()
  }

  @Test
  void should_load_build_date() {
    TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('BUILDDATE' == name) {
        return '2018-03-19T02:39:14.000Z'
      }
    }
    Toxic.loadBuildDate()
    assert '2018-03-19' == Toxic.getBuildDate()
    assert '02:39:14' == Toxic.getBuildTime()
  }

  @Test
  void should_gen_product_version_string() {
    TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
    Toxic.metaClass.'static'.loadResourceFile = { String name ->
      if('VERSION' == name) {
        return '1.2.3'
      }
      if('BUILDDATE' == name) {
        return '2018-03-19T02:39:14.000Z'
      }
    }
    Toxic.loadVersion()
    Toxic.loadBuildDate()
    assert 'Toxic - Version 1.2.3 (2018-03-19 02:39:14)' == Toxic.genProductVersionString('Toxic')
  }
}

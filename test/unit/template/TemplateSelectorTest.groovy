package template

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

class TemplateSelectorTest {
  @org.junit.BeforeClass static void beforeClass() { log.Log.configureSimpleLogging() }
  @Test
  void should_construct_a_selector() {
    assert TemplateSelector.select("email") instanceof SelectorEmail
  }

  @Test(expected = ClassNotFoundException)
  void should_throw_if_the_developer_didnt_set_the_selector_type_correctly() {
    TemplateSelector.select("INVALID")
  }
}


package template

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

class SelectorEmailTest {
  @org.junit.BeforeClass static void beforeClass() { log.Log.configureSimpleLogging() }
  @Test
  void should_select_a_template() {
    def selector = new SelectorEmail()
    selector.owner = "x"
    selector.templateType = "y"
    selector.productCode = "z"
    selector.languageTag = "en-US"
    
    assert selector.templateId == "x_y_z_en"
  }
}


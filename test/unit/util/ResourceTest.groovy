package util

import groovy.mock.interceptor.*
import org.junit.*

class ResourceTest {

  @Test
  void should_load_a_resource_as_a_stream() {
    def resource = Resource.asStream("${this.class.name.replaceAll(/\./, "/")}.class")

    assert resource instanceof InputStream
  }

  @Test
  void should_load_a_resource_as_a_reader() {
    def resource = Resource.asReader("${this.class.name.replaceAll(/\./, "/")}.class")

    assert resource instanceof Reader
  }

  @Test(expected = MissingResourceException)
  void should_throw_resource_not_found() {
    Resource.asStream("INVALID_RESOURCE")
  }

  @Test
  void should_determine_the_path_to_a_resource() {
    def path = Resource.path("${this.class.name.replaceAll(/\./, "/")}.class")
    assert path instanceof String
  }

  @Test(expected = MissingResourceException)
  void should_throw_if_resource_path_not_found() {
    Resource.path("INVALID_RESOURCE")
  }
}

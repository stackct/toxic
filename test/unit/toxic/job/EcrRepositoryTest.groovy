package toxic.job

import org.junit.*
import org.apache.log4j.Level
import org.apache.log4j.Logger
import groovy.mock.interceptor.*
import toxic.ToxicProperties
import groovy.json.*

class EcrRepositoryTest {

  def repository
  def properties = [:] as ToxicProperties

  @Before
  public void before() {
    properties['aws.ecr.profile'] = 'dummy'
    repository = new EcrRepository('foo', properties)
  }

  @Test
  public void should_return_empty_list_on_error() {
    withRuntimeMock(1, null, 'error occurred') {
      assert repository.images == [:]
    }
  }

  @Test
  public void should_empty_list_if_no_images() {
    def out = '{ "imageIds": [] }'

    withRuntimeMock(0, out, null) {
      assert repository.images == [:]
    }
  }

  @Test
  public void should_return_empty_list_if_ecr_profile_not_set() {
    repository = new EcrRepository('foo', [:] as ToxicProperties)

    withRuntimeMock(0, "", "") {
      EcrRepository.log.track { logger ->
        assert repository.images == [:]
        assert logger.isLogged("AWS profile not set", Level.WARN)
      }
    }
  }

  @Test
  public void should_return_list_of_images() {
    def out = '''{ 
      "imageIds": [ 
        { "imageTag": "a", "imageDigest": "abcdefg" },
        { "imageTag": "b", "imageDigest": "abcdefg" },
        { "imageTag": "c", "imageDigest": "zzzzzzz" }
      ] 
    }'''

    withRuntimeMock(0, out, null) {
      assert repository.images == ['abcdefg': ['a','b'], 'zzzzzzz':['c'] ]
    }
  }

  private withRuntimeMock(exitValue, out, err, closure) {
    def stub = [:]
    stub.exitValue = { -> exitValue }
    stub.waitForProcessOutput = { o,e -> 
      if (out) o.append(out)
      if (err) e.append(err)
    }

    Runtime.metaClass.exec = { String cmd, String[] envp -> stub }
    closure.call()
    Runtime.metaClass = null
  }
}
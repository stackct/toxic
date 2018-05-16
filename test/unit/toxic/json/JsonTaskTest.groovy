package toxic.json

import org.junit.After
import org.junit.Test
import toxic.ToxicProperties
import toxic.http.HttpTask

class JsonTaskTest {
  @After
  void after() {
    HttpTask.metaClass = null
  }

  @Test
  void should_set_int_response_value_without_quotes() {
    def request = '{ "foo": "bar" }'
    def expectedResponse = '{ "foo": %=bar% }'
    def actualResponse = '{ "foo": 1 }'
    def props = runTask(request, expectedResponse, actualResponse)
    assert 1 == props.bar
  }

  @Test
  void should_set_int_response_value_with_quotes() {
    def request = '{ "foo": "bar" }'
    def expectedResponse = '{ "foo": "%=bar%" }'
    def actualResponse = '{ "foo": 1 }'
    def props = runTask(request, expectedResponse, actualResponse)
    assert 1 == props.bar
  }

  def runTask(String request, String expectedResponse, String actualResponse, def customProps = [:]) {
    def props = mockProps(customProps)
    mockFiles(request, expectedResponse) { requestFile, responseFile ->
      HttpTask.metaClass.transmit = { req, expectedResp, memory ->
        actualResponse
      }
      JsonTask jsonTask = new JsonTask(input: requestFile, reqContent:request, props: props)
      jsonTask.doTask(props)
    }
    props
  }

  def mockProps(customProps) {
    def props = new ToxicProperties()
    customProps.each { k, v ->
      props.put(k, v)
    }
    props
  }

  def mockFiles(String request, String response, Closure c) {
    def requestFile
    def responseFile
    try {
      requestFile = File.createTempFile('JsonTaskTest', '_req.json')
      requestFile.text = request

      responseFile = new File(requestFile.parent, requestFile.name.replace('_req', '_resp'))
      responseFile.text = response

      c(requestFile, responseFile)
    }
    finally {
      requestFile?.delete()
      responseFile?.delete()
    }
  }
}

package toxic.json

import org.junit.Test
import groovy.mock.interceptor.*
import toxic.ToxicProperties
import toxic.http.HttpTask

class JsonTaskTest {

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

  @Test
  void should_set_json_response_property() {
    testSetResponseProperties(null, [:])
    testSetResponseProperties('INVALID', [:])
    testSetResponseProperties('{ "status": "ok" }', [status: 'ok'])
  }

  private void testSetResponseProperties(String response, def expected) {
    def props = [:] as ToxicProperties
    def task = new JsonTask()

    task.metaClass.baseResponseProperties = { String r, ToxicProperties mem -> mem['http.response.body'] = response }
    task.setResponseProperties(null, props)

    assert props['http.response.json'] == expected
  }

  @Test
  void should_default_content_type_to_json() {
    def json = '{ "foo": "bar" }'
    def props = runTask(json, json, json, [httpMethod:'GET'])
    assert 'application/json' == props['http.header.Content-Type']
  }

  @Test
  void should_handle_request_with_no_response_file() {
    def json = '{ "foo": "bar" }'
    def props = runTask(json, null, json, [httpMethod:'GET'])
    assert 'application/json' == props['http.header.Content-Type']
  }

  def runTask(String request, String expectedResponse, String actualResponse, def customProps = [:]) {
    def props = mockProps(customProps)
    
    mockFiles(request, expectedResponse) { requestFile, responseFile ->
      JsonTask jsonTask = new JsonTask() {
          @Override
          protected String transmit(r, e, m) {
            return actualResponse
          }
      }
      jsonTask.input = requestFile
      jsonTask.reqContent = request
      jsonTask.props = props
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

      if(null != response) {
        responseFile = new File(requestFile.parent, requestFile.name.replace('_req', '_resp'))
        responseFile.text = response
      }

      c(requestFile, responseFile)
    }
    finally {
      requestFile?.delete()
      responseFile?.delete()
    }
  }
}

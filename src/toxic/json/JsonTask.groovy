package toxic.json

import groovy.json.*
import org.apache.log4j.Logger
import toxic.JsonValidator
import toxic.ToxicProperties
import toxic.http.HttpTask

class JsonTask extends HttpTask {
  private static Logger log = Logger.getLogger(HttpTask.class.name)

  def validator = new JsonValidator()
  def parser = new JsonSlurper()

  @Override
  def prepare(def request) {
    request = validator.normalizeRequest(request, props)
    super.prepare(request)
  }

  @Override
  String lookupExpectedResponse(File file) {
    String responseJson = super.lookupExpectedResponse(file)
    validator.normalizeResponse(responseJson ?: "")
  }

  @Override
  void validate(String actualResponse, String expectedResponse, ToxicProperties memory) {
    validator.init(memory)
    validator.validate(actualResponse, expectedResponse, memory)
  }

  @Override
  protected String headers() {
    props['http.header.Content-Type'] = 'application/json'
    return super.headers()
  }

  @Override
  protected void setResponseProperties(String response, ToxicProperties memory) {
    baseResponseProperties(response, memory)

    try {
      memory['http.response.json'] = parser.parseText(memory['http.response.body'])
    }
    catch (JsonException | IllegalArgumentException e) {
      log.warn("HTTP response body did not contain valid JSON")
    }
  }

  // This method had to be extracted in order to mock it since calls to super() cannot be
  // directly mocked. Don't @ me.
  protected void baseResponseProperties(String response, ToxicProperties memory) {
    super.setResponseProperties(response, memory)
  }
}

package toxic.json

import toxic.JsonValidator
import toxic.ToxicProperties
import toxic.http.HttpTask

class JsonTask extends HttpTask {
  def validator = new JsonValidator()

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
}

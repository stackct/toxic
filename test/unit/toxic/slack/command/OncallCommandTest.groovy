package toxic.slack.command

import org.junit.*

import toxic.*
import javax.servlet.http.*

public class OncallCommandTest extends CommandTest {

  def victoropsJson = """{"team":"Misc","schedule":[
  {"rotationName":"all-Misc","shiftName":"2-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample7","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample7","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample7","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample7","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample7","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"1-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample8","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample8","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample8","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample8","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample8","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"3-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample9","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample9","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample9","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample9","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample9","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"4-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample4","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample4","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample4","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample4","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample4","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"1-pac","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample1","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample1","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample1","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample1","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample1","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"5-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample6","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample6","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample6","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample6","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample6","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"6-est-8-5","rolls":[
    {"change":"2016-12-06T08:00:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"sample5","isRoll":true},
    {"change":"2016-12-07T08:00:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"sample5","isRoll":false},
    {"change":"2016-12-08T08:00:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"sample5","isRoll":false},
    {"change":"2016-12-09T08:00:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"sample5","isRoll":false},
    {"change":"2016-12-12T08:00:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"sample5","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:00:00-05:00"},
  {"rotationName":"all-Misc","shiftName":"7-est","rolls":[
    {"change":"2016-12-06T08:30:00-05:00","until":"2016-12-06T17:00:00-05:00","onCall":"","isRoll":true},
    {"change":"2016-12-07T08:30:00-05:00","until":"2016-12-07T17:00:00-05:00","onCall":"","isRoll":false},
    {"change":"2016-12-08T08:30:00-05:00","until":"2016-12-08T17:00:00-05:00","onCall":"","isRoll":false},
    {"change":"2016-12-09T08:30:00-05:00","until":"2016-12-09T17:00:00-05:00","onCall":"","isRoll":false},
    {"change":"2016-12-12T08:30:00-05:00","until":"2016-12-12T17:00:00-05:00","onCall":"","isRoll":false}],
    "policyType":"RotationGroupNextPolicy","shiftRoll":"2016-12-06T08:30:00-05:00"},
  {"onCall":"sample1","rotationName":"Infrastructure","shiftName":"Infrastructure","rolls":[
    {"change":"2016-12-05T09:00:00-08:00","until":"2016-12-12T09:00:00-08:00","onCall":"sample1","isRoll":true}],
    "policyType":"RotationGroupPolicy","shiftRoll":"2016-12-05T09:00:00-08:00"},
  {"onCall":"sample4","overrideOnCall":"sample2","rotationName":"DevOpser","shiftName":"DevOpser","rolls":[
    {"change":"2016-12-05T09:00:00-05:00","until":"2016-12-12T09:00:00-05:00","onCall":"sample4","isRoll":true}],
    "policyType":"RotationGroupPolicy","shiftRoll":"2016-12-05T09:00:00-05:00"},
  {"onCall":"sample3","rotationName":"DataDevOps","shiftName":"DataDevOpser","rolls":[
    {"change":"2016-12-05T09:00:00-05:00","until":"2016-12-12T09:00:00-05:00","onCall":"sample3","isRoll":true}],
    "policyType":"RotationGroupPolicy","shiftRoll":"2016-12-05T09:00:00-05:00"},
  {"onCall":"sample3","rotationName":"DataDevOps","shiftName":"DataDevOpser","rolls":[
    {"change":"2016-12-05T09:00:00-05:00","until":"2016-12-12T09:00:00-05:00","onCall":"sample3","isRoll":true}],
    "policyType":"RotationGroupPolicy","shiftRoll":"2016-12-05T09:00:00-05:00"}],
"overrides":[]}"""

  @Test
  void should_lookup_oncall_victorops() {
    def reqK = []
    def reqV = []
    def method
    def output
    def code = HttpServletResponse.SC_OK
    URL.metaClass.openConnection = { new Object() {
      def setRequestProperty(k,v) { reqK << k; reqV << v }
      def setRequestMethod(m) { method = m }
      def setDoOutput(b) { output = b }
      def getInputStream() { [text:victoropsJson] }
      def getResponseCode() { code }
      }}
    def actual = sh.handleCommand(bot, [user: 'id1', text: ".oncall foo"])
    assert actual == "Currently on-call for foo: [sample1, sample2, sample3]"
    assert !output
    assert method == "GET"
    assert reqK.contains("Accept")
    assert reqV.contains("application/json")

    actual = sh.handleCommand(bot, [user: 'id1', text: ".oncall", channel: "squad_tps"])
    assert actual == "Currently on-call for tps: [sample1, sample2, sample3]"

    code = HttpServletResponse.SC_FORBIDDEN
    actual = sh.handleCommand(bot, [user: 'id1', text: ".oncall", channel: 'foo'])
    assert actual == "Failed to lookup oncall personnel for foo"
  }
 
  @Test
  void should_fail_to_lookup_oncall_victorops() {
    def code = HttpServletResponse.SC_OK
    URL.metaClass.openConnection = { new Object() {
      def setRequestProperty(k,v) { throw new Exception("ruh roh") }
      }}
    def actual = sh.handleCommand(bot, [user: 'id1', text: ".oncall foo"])
    assert actual == "Failed to lookup oncall personnel for foo"
  }

}
// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic

import org.junit.*
import groovy.mock.interceptor.*

public class SerializedFormatterTest {
  @Test
  public void testParse() {
    def props = new ToxicProperties()
    def file = File.createTempFile(this.class.name, null)
    try {
      def sf = new SerializedFormatter()
      props.serializedFile = file.canonicalPath
      props.serializedSummaryFile = file.canonicalPath + ".summary"
      sf.init(props)
      def result = new ArrayList<TaskResult>()
      result << new TaskResult("1","2","3","4")
      result << new TaskResult("a","b","c","d")
      sf.format(result)
      sf.formatSummary([foo:1,bar:2])
      def actual = sf.parse()
      assert actual.size() == 2
      assert actual[0].id == "1"
      assert actual[1].id == "a"
      def summary = sf.parseSummary()
      assert summary.foo == 1
      assert summary.bar == 2
    } finally {
      try {
        file.delete()
        new File(props.serializedSummaryFile).delete()
      }catch (Exception e) {
        // we tried to clean-up....
      }
    }
  }
}
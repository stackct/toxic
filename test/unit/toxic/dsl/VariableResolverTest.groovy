package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class VariableResolverTest {
  
  @Test
  void should_resolve_missing_property() {
    TestCase testCase = new TestCase(steps: [new Step()], vars:[k1:'v1'])
    def props = [testCases:[testCase], stepIndex: 0]
    
    assert 'v1' == new VariableResolver(props).propertyMissing('k1')
    assert null == new VariableResolver(props).propertyMissing('k2')
  }
}

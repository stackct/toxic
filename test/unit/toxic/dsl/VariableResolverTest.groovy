package toxic.dsl

import org.junit.Test

class VariableResolverTest {
  
  @Test
  void should_resolve_missing_property() {
    TestCase testCase = new TestCase(steps: [new Step(function: 'foo')], vars:[k1:'v1'])
    def props = [testCase:testCase, functions: ['foo': new Function()]]
    
    assert 'v1' == new VariableResolver(props).propertyMissing('k1')
    assert null == new VariableResolver(props).propertyMissing('k2')
  }
}

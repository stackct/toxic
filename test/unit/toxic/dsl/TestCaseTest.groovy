package toxic.dsl

import org.junit.Test

class TestCaseTest {

  @Test
  void should_normalize_input() {
    new TestCase().with { parser ->
      assert 'test "foo", {' == parser.normalize('test "foo" {')
      assert 'test "foo", {' == parser.normalize('test "foo", {')
      assert 'test "foo", {' == parser.normalize('test "foo",{')
      assert 'test "foo", ' == parser.normalize('test "foo"')
    }
  }

  @Test
  void should_parse_a_complete_test_case() {
    def input = { ->
      test "foo-test", {
        description "Description for foo"
        tags 'foo', 'bar'

        declare {
          ying "yang"
        }

        step "fn-dummy", "foo-step-1", {
          argument1   1
          argument2   2
        }

        step "fn-dummy-wait", "foo-step-2", {
          wait {
            timeoutMs  10
            intervalMs  5

            condition {
              eq "1", "2"
            }
          }
        }

        assertions {
          eq  "{{ foo-step-1.output1 }}", "foo.output.1"
          neq "{{ foo-step-1.output2 }}", "bar.output.2"
        }
      }
    }

    Parser.parse(new TestCase(), input).with { tests ->
      assert tests[0].name == 'foo-test'
      assert tests[0].description == 'Description for foo'
      assert tests[0].tags == ["foo", "bar"] as Set
      assert tests[0].vars == [ying:'yang']
      assert tests[0].steps.size() == 2
      tests[0].steps[0].with { step ->
        assert step.name == 'foo-step-1'
        assert step.function == 'fn-dummy'
        assert step.args.size() == 2
        assert step.args['argument1'] == 1
        assert step.args['argument2'] == 2
      }
      tests[0].steps[1].with { step ->
        assert step.name == 'foo-step-2'
        assert step.function == 'fn-dummy-wait'
        assert step.args.size() == 0
        assert step.wait.timeoutMs == 10
        assert step.wait.intervalMs == 5
        assert false == step.wait.retryCondition()
      }
      assert tests[0].assertions.size() == 2
      assert 'assert \'{{ foo-step-1.output1 }}\' == \'foo.output.1\' : " foo-step-1.output1  == foo.output.1"' == tests[0].assertions[0]
      assert 'assert \'{{ foo-step-1.output2 }}\' != \'bar.output.2\' : " foo-step-1.output2  != bar.output.2"' == tests[0].assertions[1]
    }
  }

  @Test
  void should_parse_with_step_map_values() {
    def input = """
      test "single line item" {
        description "Terminal should display a single line item"
    
        step "display_order", "single line item", {
            lineItems ([ [sku: "21", description: "Line Item #1" ] ])
            total     ([amount: [currency: "USD", amount: 1000]])
        }
      }
    """
    def results = TestCase.parse(input)
    assert 1 == results.size()
    def testCase = results[0]
    assert 'single line item' == testCase.name
    assert 'Terminal should display a single line item' == testCase.description
    assert 1 == testCase.steps.size()
    def step = testCase.steps[0]
    assert 'display_order' == step.function
    assert 'single line item' == step.name
    assert [lineItems: [[sku: '21', description: 'Line Item #1']], total: [amount: [currency: 'USD', amount: 1000]]] == step.args
    assert [] == testCase.assertions
  }

  @Test
  void should_create_assertion_file() {
    TestCase testCase = new TestCase(name: 'testCase', assertions: ['assert 1=1', 'assert 2=2'])
    File parent = new File('parent')
    TransientDir transientDir = testCase.assertionFile(parent) { contents ->
      contents.replaceAll('=', '==')
    }
    assert transientDir.exists()
    assert transientDir.isDirectory()
    assert 1 == transientDir.listFiles().size()

    TransientFile file = transientDir.listFiles()[0]
    assert parent == file.parentFile
    assert file.exists()
    assert !file.isDirectory()
    assert file.name.startsWith('testCase_assertions_')
    assert file.name.endsWith('.groovy')
    assert 'assert 1==1\nassert 2==2\n' == file.text
  }

  @Test
  void should_override_to_string() {
    assert 'testName [testDescription]' == new TestCase(name: 'testName', description: 'testDescription').toString()
  }
}

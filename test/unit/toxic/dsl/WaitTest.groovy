package toxic.dsl

import toxic.ToxicProperties
import org.junit.Test
import toxic.ToxicPropertiesTest

class WaitTest {

    @Test
    public void should_validate_required_fields() {
        def testCases = [
            { -> wait { } },
            { -> wait { timeoutMs(1) } },
            { -> wait { timeoutMs(1); intervalMs(2) } },
        ]

        testCases.each { tt ->
            try {
                Parser.parse(new Wait(), tt)
                fail "Should not have succeeded"
            } catch (IllegalArgumentException e) {
            }
        }
    }

    @Test
    public void should_set_default_wait_values() {
        def input = { ->
            wait {
                timeoutMs  1
                intervalMs 2
                condition { eq "foo", "bar" }
            }
        }

        Parser.parse(new Wait(), input).with { waits ->
            assert waits.size() == 1
            assert waits[0].successes == 1
        }
    }

    @Test
    public void should_parse_wait_statement() {
        def input = { ->
            wait {
                timeoutMs  1
                intervalMs 2
                successes  3

                condition {
                    eq "foo", "bar"
                }

                condition {
                    eq "baz", "bla"
                }
            }
        }

        Parser.parse(new Wait(), input).with { waits ->
            assert waits.size() == 1
            assert waits[0].timeoutMs == 1
            assert waits[0].intervalMs == 2
            assert waits[0].successes == 3
            assert waits[0].conditions.size() == 2
            assert waits[0].conditions[0].assertions.size() == 1
            assert waits[0].conditions[1].assertions.size() == 1
            assert waits[0].conditions[0].assertions instanceof List<String>
        }
    }

    @Test
    public void should_build_false_retry_closure() {
        def wait = new Wait()
        wait.conditions << new Condition(assertions: ['assert 1 == 1', 'assert 2 == 1'])
        def retryCondition = wait.getRetryCondition([:] as ToxicProperties)

        assert retryCondition instanceof Closure
        assert false == retryCondition()
    }

    @Test
    public void should_build_true_retry_closure() {
        def wait = new Wait()
        wait.conditions << new Condition(assertions: ['assert 1 == 1', 'assert 2 == 2'])
        def retryCondition = wait.getRetryCondition([:] as ToxicProperties)

        assert retryCondition instanceof Closure
        assert true == retryCondition()
    }

    @Test
    public void should_build_with_multiple_conditions() {
        def wait = new Wait()
        wait.conditions << new Condition(assertions: ['assert 1 == 1', 'assert 1 == 2'])
        wait.conditions << new Condition(assertions: ['assert 1 == 1', 'assert 2 == 2'])

        def retryCondition = wait.getRetryCondition([:] as ToxicProperties)
        assert true == retryCondition()
    }

    @Test
    public void should_build_retry_closure_with_interpolation() {
        def props = [foo:'bar'] as ToxicProperties

        def wait = new Wait()
        wait.conditions << new Condition(assertions: ['assert "{{ foo }}" == "bar"'])

        def retryCondition = wait.getRetryCondition(props)
        assert true == retryCondition()
    }
}

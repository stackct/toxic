package toxic.dsl

import toxic.groovy.GroovyEvaluator
import toxic.ToxicProperties

class Wait extends Parser {
    private static final List<String> requiredFields = ['timeoutMs', 'intervalMs', 'conditions']

    int timeoutMs
    int intervalMs
    int successes = 1
    boolean retry = false
    List<Condition> conditions = []

    void wait(Closure closure) {
        def wait = new Wait()
        closure.setResolveStrategy(Closure.DELEGATE_FIRST)
        closure.delegate = wait
        closure()
        wait.validate()

        results << wait
    }

    def timeoutMs(int value) {
        this.timeoutMs = value
    }

    def intervalMs(int value) {
        this.intervalMs = value
    }

    def successes(int value) {
        this.successes = value
    }

    def retry(boolean value) {
        this.retry = value
    }

    def condition(Closure closure) {
        def condition = new Condition()

        closure.setResolveStrategy(Closure.DELEGATE_FIRST)
        closure.delegate = condition
        closure()

        conditions << condition
    }

    Closure getRetryCondition(ToxicProperties props) {
        return { ->
            null != conditions.find { condition ->
                try {
                    condition.assertions.each { assertion ->
                        new GroovyEvaluator().eval(Step.interpolate(props, assertion))
                    }
                    return true
                } catch (AssertionError e) {
                    return false
                }
            }
        }
    }

    void validate() {
        def missingFields = []
        requiredFields.each { field ->
            if(!this."${field}") { missingFields << field }
        }

        if(missingFields) {
            throw new IllegalArgumentException("Missing required fields for wait; fields=${missingFields}")
        }
    }
}

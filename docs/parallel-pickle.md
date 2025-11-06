## Considerations

* Resource limitation on Toxic process running so many parallel operations
* Sauce Labs limitation on concurrent sessions
* Functions that use unique identifiers need to guarantee uniqueness, such as `RandomString()`
* HTTP response interceptor

## TaskOrganizer Approach

one.test
    fn:: One -> {{ libPath }}/one (dirItem)
two.test
    fn:: Two -> {{ libPath }}//two (dirItem)


The DirOrganizer would encounter these and treat them as TestCaseTask, and the init() would do what TestCaseHandler is doing today, as far as parsing the file and loading up the steps and their
respective paths. This would return a task to TaskMaster, which then call .execute() on it.  

```
public class TestCaseTask extends FileTask {
    public abstract List<TaskResult> doTask(def memory)  { // Real work goes here
        /* 
        parse and return task results indicating that parsing was done, but more importantly,
        set the collected TestCase metadata for deferred execution. This is what will allow
        for a dry run, or documentation export, validation, etc
        */
        memory['pickle.tests] << parsedTests
    }

}
```

* TestCaseTask
    execute() -> parse and task results indicating what was done, but more importantly,
        set the collected TestCase metadata for deferred execution. This is what will allow
        for a dry run, or documentation export, validation, etc

* TaskMaster.doRep() 
    -> Completes collecting
    -> Start threadPool
    -> Construct TestCaseOrganizer per testCase in memory['pickle.tests']
        -> hasNext() returns false // Only a single test per organizer
        -> next() returns null     // Only a single test per organizer
        * This is because we want to re-use the DirOrganizer for the step/tasks



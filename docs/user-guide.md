# User Guide

Toxic is a generic tool used to automate a series of sequential tasks. Tasks can be anything, really, but a common use for Toxic is to run automated tests. Toxic relies on a Task Master to coordinate the execution of Tasks. The Task Master then relies on a Task Organizer to feed it tasks.

### User Audience

The primary users of this project are devops staff looking to automate tasks, such as integration testing, continuous integration builds, deployments, etc.

### Tasks

* Groovy tasks - Arbitrary Groovy script. The most flexible type of task, but requires Java or Groovy coding knowledge.
* HTTP tasks - An HTTP request. This can actually be raw TCP data, but most users will use this task to feed XML or HTML content to a server for automated testing. This task can also look for a corresponding response and perform comparisons to validate that the actual server response matches the expected response.
* Properties tasks - key/value pairs following the standard Java Properties format
* File tasks - Useful for comparing text files to each other.
* SQL tasks - Useful for running SQL queries against a JDBC database and comparing the results to an expected result.
* Exec tasks - Runs shell processes on remote hosts in a Groovy-like syntax

### Embedded Groovy

The HTTP, Properties, and File tasks all allow embedded Groovy script. Each groovy script will have multiple variables exposed by default:

* memory - A map of key/value pairs that have been collected from all prior Properties tasks.
* log - a Logger for logging interesting information
* input - The original input object supplied to the Task implementation. Typically a File but other Directory Organizers could specify a String or other objects, depending on what the Task implementations support.

For example, you might have a test.properties file that contains:

```
foo=123
bar=`memory.foo + 1000
```

Now suppose an HTTP request task references the `bar` variable, it will be replaced with the value `1123`. Ex:

```
<acme value="%bar%"/>
```

Will translate to: 

```
<acme value="1123"/>
```

Be aware that the groovy script is not evaluated until the property is pulled from the property map. This allows the property to change over time, for example, if the script was designed to retrieve the current time. However, there may be occasions where you need the property value set to a firm value, and want the groovy script evaluated immediately when the property is added to the memory map. Use this notation to force an immediate evaluation: 

```
bar=``memory.foo + 1000``
```

### Variables

The HTTP request can include embedded variables, using a `%myVariable%` syntax. Ex:

```
<foo bar="%myVariable%"/>
```

If `myVariable` equals "Hello", then the actual request sent to the server will be:

```
<foo bar="Hello"/>
```

#### Assignment (%=...%)

Likewise, the HTTP response can include these same embedded variables and script but also can include content manipulation and variable assignment. For example, if you know the server will respond with an ID that you want to re-use later, you can include this syntax in the response XML:

```
<foo bar="%=myVariableID%"/>
```

If the actual response contains:

```
<foo bar="123"/>
```

Then the `myVariable` now contains the string "123".

#### Validation and Assignment (%=...=...%)

You can also check the response against a variable while also assigning that to another variable.  This is useful when the first variable is one you don't want to change (e.g. a global, dynamic variable)

```
<foo bar="%=myNewVariable=myGeneratedID%"/>
```

So if `myGeneratedId` has '123', the following example would be validated that it's `bar` value is `123`, and also set the value `123` into the `myNewVariable` variable:

```
<foo bar="123"/>
```

#### Variable Multiplicity (%=...++%)

It may be necessary to store large numbers of variables with similar names, but where it may not be known which variables have already been used. To avoid the risk of overwriting variables, or having to keep track of large lists of similarly-named variables, consider using incrementing variables. Ex:

```
<foo bar="%=myVariableID++%"/>
```

Notice that the variable names ends with `++`. This notation causes the variable name to have a number appended to the end, such that it is guaranteed no other variable with that same name and number has been used. In the above example, if there was only one assignment using that syntax then there would exist just one variable named `myVariableID0` and it would contain the value that was assigned to it. Now if another assignment was performed to that same variable syntax:

```
<foo bar="%=myVariableID++%"/>
```

Then there would now be two variables, `myVariableID0` and `myVariableID1`.
Further, a variable called `myVariableID_count` will also exist automatically, and will contain an integer value, which in this example now is going to have a value of `2`.

Suppose that you used this above incrementing variable syntax to store a sequence of numbers. Ex:

```
myVariableID0=432
myVariableID1="2.11"
myVariableID2=100.0
```

You can quickly substitute the sum of those variable values using the following notation:

```
<foo bar="%+myVariableID%"/>
```

In this example, the above text would be replaced with:

```
<foo bar="534.11"/>
```

Further you can quickly substitute the last value set into those variables using the following notation:

```
<foo bar="%!myVariableID%"/>
```

In this example, the above text would be replaced with:

```
<foo bar="100.0"/>
```

Note that this same functionality exists even if referencing the memory map directly within a Groovy task. Ex:

```
memory["myVariableID++"] = 432
memory["myVariableID++"] = "2.11"
memory["myVariableID++"] = 100.0
assert memory.get("+myVariableID") == 534.11
```

### Text Validation

When comparing server responses to an expected response, often you may want to ignore a portion of the response since it could be random or generated value, such as a timestamp. This can be accomplished by embedded special characters in the expected response text.

#### Character-delimited Text Ignore

Suppose a server responds with an HTTP response that includes a time and date in an attribute:

```
<foo bar="12/31/2011 12:12:13.000 GMT"/>
```

If you want to ignore that timestamp value, replace it with %% in your expected HTTP content:

```
<foo bar="%%"/>
```

When the text validator runs, it will mark those two HTTP texts are identical.
Suppose, you want to ignore just 6 characters of a TCP server response:

```
PKZ3455-25_235523xxhw345511035233_123111x
```

#### Length-based Text Ignore

To ignore those 6 characters representing the date near the end of that text stream, replace those characters with %#6%:

```
PKZ3455-25_235523xxhw345511035233_%#6%x
```

#### Variable Padding and Truncation

Toxic can also do inline variable padding and truncation. Suppose we have a variable `foo=25`. But technically it could have three characters. But the server is programmed to response with a hyphen for any missing character. We can still have a successful match by using padding notation as follows:

```
PKZ3455%foo,-3,-%_235523xxhw345511035233_%#6%x
```

The format is as follows: `%<variable_name>[,required_length[,padding_char]]%`. If the required_length is negative, it will left pad. If positive, it will right pad.
Further, if the variable `foo=12345`, using that same notation, we can have it truncate the value to just three characters. With the required_length set to negative, it will chop off the leading 2 characters, resulting in the following translation:

```
PKZ3455345_235523xxhw345511035233_%#6%x
```

#### Variable Length Skip Pattern

By default `%%` skips forward to the next single character match. For an HTTP structure where angle brackets abound this makes skipping tags impossible. Instead, skipping based on a number of characters allows us to jump to and end tag. The following will search forward for `</or` so if there is an intervening <notes/> tag it will be ignored.

```
  %>4%
</orderReceipt>
```

#### Skip Remaining

Skip the remaining content.

```
%*%
```

#### HTTP Headers

HTTP method and headers can optionally be specified in the HTTP request and response text. If specified in the request, then any dynamically generated method and headers will be omitted. (Dynamically generated headers are specified via the `xml.header.<1-9>` properties). If specified in the expected HTTP response, then the entire server response will be text matched. Otherwise, just the body of the HTTP response will be compared.

This is useful for testing that does not need to be concerned with HTTP headers and instead just needs to focus on the content.

### Examples

#### XML/HTTP Example

Suppose a file exists called `30_submitRequest_req.xml`. This file contains HTTP content that will be transmitted to a webserver:

```
GET /import?csv=%csvFile% HTTP/1.1
Host: %webHost%
Connection: close
```

Notice the embedded, variables that are passed onto the URL query string. This allows quick changes to the test input without having to modify the XML. New values for these variables can set in a properties file, such as `01_init.properties`, which will load before the `30_submitRequest_req.xml` (since `10_` is sorted before `30_`), or can be passed in via the command line, such as:

```
bin/toxic -doDir=~/toxic -csvFile=/tmp/myimport.csv
```

Then, the corresponding response file, `30_submitRequest_resp.xml` contains the following HTML content:

```
%%
Process Complete!
CSV File: %csvFile%
%%
```

Notice the use of embedded variables that allow for dynamic, but precise matching of the actual response to an expected response.

#### Groovy Script Example

Consider the use of `.groovy` files that contain isolated scripts that perform specific functions. One particular file might be named `20_prepareCsv.groovy`:

```
// Send the CSV to the web server
def prepareCsv = { webHost ->
  def cmd = "scp ${memory.csvFile} ${memory.sshUser}@${webHost}:${memory.csvFile}"
  assert 0 == exec(cmd)
}
 
prepareCsv(memory.web1Host)
prepareCsv(memory.web2Host)
```

This script uploads the CSV file to both of the webserver, where the web app might look for them during the HTTP request mentioned earlier.

Notice that the SCP call is wrapped by a simple `exec()` closure that is built-in to the Toxic-Groovy Task. This makes it simple to execute system commands. However, there are also task files ending in `.exec` and other methods to accomplish this.

*NOTE* This example is not a very secure example. It's for illustrative purposes only.

#### File Comparison Task Example

Two or more files can be compared against each other to detect mismatches.  If Toxic discovers a file following the format `<prefix>_1.file` then it will compare the contents of that file with all other files in the same directory that follow the pattern `<prefix>_[2-9].file`.  All standard Toxic validation logic is available, including variable substition, wildcards with `%%`, and embedded groovy script. For example, suppose you have the following directory structure:

```
test/
  my_1.file
  my_2.file
  my_3.file
```

* and `my_1.file` contains: `hello %%!`
* and `my_2.file` contains: `hello jack!`
* and `my_3.file` contains: `hello bart!`

Toxic will pass that test since all 3 files match.

### Other Tips and Tricks

#### Halting Test on Failure

Anytime you want to stop a test directory on it's first failure use the following flag on the command line, (or define the property in a `.properties` file that the Toxic directory traversal will locate):

```
-tmHaltOnError=true
```

#### Truncated errors

Toxic will truncate Task errors by default. To override this behavior, set the maxErrorLength property.

```
-maxErrorLength=5000
```

#### Running an entire test suite multiple times

If you want to run a test suite multiple times use the flag `-tmReps=1000` to set the number times to run the test. If you want to use multiple threads use the flag `-agentTaskMasterCount=5` to set the number of threads.

### Typical Usage Scenario

Assuming you have the Toxic tool downloaded and unzipped, you can now run it against a directory of tasks. Let's suppose you have a directory at `/home/foo/tasks`. Navigate to the directory where you installed Toxic (or set the `TOXIC_HOME` environment variable appropriately).

Note in the following examples that if you are running Toxic outside of the Toxic installation directory, you must have an environment variable `TOXIC_HOME` pointing to the Toxic installation directory and your PATH environment variable must have `$TOXIC_HOME/bin` included. Then where you see `bin/toxic` below, just specify `toxic`.

Run the following command to start Toxic and process those tasks:

```
bin/toxic -doDir=/home/foo/tasks
```

Add verbose HTTP logging:

```
bin/toxic -doDir=/home/foo/tasks -httpVerbose=true
```

Override the default log level:

```
bin/toxic -doDir=/home/foo/tasks -logLevel=debug
```

Define a custom variable:

```
bin/toxic -doDir=/home/foo/tasks -foo=1234
```

Override an existing variable defined in the `conf/toxic.properties` file:

```
bin/toxic -doDir=/home/foo/tasks -httpHost=1.2.3.4
```

Specify a command line override value that contains spaces.  Notice this is a double quoted value wrapped with single quotes.

```
bin/toxic -doDir=/home/foo/tasks -httpMethod='"POST /api/orders HTTP/1.1"'
```

Specify additional properties files, override variables within the default properties file, and execute a specific task file:

```
bin/toxic -doDir=/home/foo/tasks/10_demo/01_acme.xml -httpHost=1.2.3.4 -httpPort=8080 /home/foo/tasks/tasks.properties
```

### Directory-Based Tasks

With the default Toxic configuration, each task is representing as a file, and multiple files can be grouped into a single directory. Further a directory can contain a subdirectory of more files, representing a large nested and ordered set of tasks.

#### Links

Additionally, it's possible to link task files into other directories. Suppose you have two high-level task groups, located in two directories:

```
- tasks
  |- 00_demoA
  |  |- 10_fooA_req.xml
  |  |- 10_fooA_resp.xml
  |  |- 20_barA_req.xml
  |  |- 20_barA_resp.xml
  |- 10_demoB
     |- 10_taskB_req.xml
     |- 10_taskB_resp.xml
```

Now if you want to run the demoB task set, you would specify:

```
bin/toxic -doDir=tasks/10_demoB
```

However, you might also want to include just `20_barA` in this task group, but do not want to run `10_fooA`. You can do this by added a `20_barA.link` file to the `10_demoB` directory, where each line of the `.link` file contains an absolute or relative path to the target task file (or directory). Ex:

```
- tasks
  |- 00_demoA
  |  |- 10_fooA_req.xml
  |  |- 10_fooA_resp.xml
  |  |- 20_barA_req.xml   (target task)
  |  |- 20_barA_resp.xml
  |- 10_demoB
     |- 10_taskB_req.xml
     |- 10_taskB_resp.xml
     |- 20_barA.link  (link file contains this line: ../00_demoA/20_barA_req.xml)
```

This link technique allows for inclusion of remote tasks without having to duplicate the task file itself, therefore reducing file maintenance.

#### Disabling Files and Directories

To prevent Toxic from running a task file or directory of task files, simply rename that file or directory to have a .disabled or .ignore suffix. For example, change:

```
  |- 10_demoB
```

to

```
  |- 10_demoB.disabled
```

And now every task in that directory will be skipped.

#### Conditional Execution of Files and Directories

Files with a `.if` extension can be used to dynamically control the execution of task files. When Toxic encounters a file ending with a `.if` extension, it will evaluate the contents of that file as Groovy script, and only if the result of the evaluation is _true_ will it continue to execute the remaining tasks in the directory. If the script evaluates to _false_ then Toxic will stop executing the current directory, and will pop back up to the parent directory and continue looking for more tasks. For example, given the above hierarchy, support you want to only execute the `00_demoA` directory of tasks if the property `demoAEnabled` is _true_. Create a file in the `00_demoA` directory named `01_enabled.if` containing the following line:

```
return memory.demoAEnabled
```
```
- tasks
  |- 00_demoA
  |  |- 01_enabled.if
  |  |- 10_fooA_req.xml
  |  |- 10_fooA_resp.xml
  |  |- 20_barA_req.xml
  |  |- 20_barA_resp.xml
  |- 10_demoB
     |- 10_taskB_req.xml
     |- 10_taskB_resp.xml
```

Without this `demoAEnabled` property declared, Toxic will now skip all the tasks in the `demoA` directory. However, if the desire is to enable these tasks, you can add the `-demoAEnabled=true` argument to the `toxic` command, or declare the property in a `.properties` file.

If you want some tasks executed in the task directory, simply name the `.if` file with a higher sequence number, and only those tasks that follow will be skipped. Ex:

```
- tasks
  |- 00_demoA
  |  |- 10_fooA_req.xml
  |  |- 10_fooA_resp.xml
  |  |- 15_enabled.if
  |  |- 20_barA_req.xml
  |  |- 20_barA_resp.xml
  |- 10_demoB
     |- 10_taskB_req.xml
     |- 10_taskB_resp.xml
```

### Multi-threading, Repetition, Iteration

Toxic supports multiple threads (Task Masters) via a single instance of Toxic. Additionally, each Task Master can be configured to repeat it's task set multiple times. Finally, an individual task within a task set can be programmed to repeat multiple times within each repetition.

* Multiple Threads - By default, Toxic will spawn a single Task Master thread. To configure multiple Task Masters, set the `agentTaskMasterCount` property to the desired number of threads.
* Multiple Repetitions - By default, Toxic will run through a task set just once. To configure for multiple reps, set the `tmReps` property to the desired number of reps. Ex: `-tmReps=10`
* Mulitple Iterations - By default, Toxic will execute each task just once, within each repetition. Configuring for multiple iterations requires Groovy script. This can be done multiple ways, but the most common way is to surround the target task with two Groovy tasks. Consider a directory-based Task Organizer:

```
- tasks
  |- 00_demo
     |- 10_task_req.xml  (this is the XML request that will be sent to the server)
     |- 10_task_resp.xml  (this is the XML response that will be compared with the server response)
```

Now you want the `10_task_req.xml` task to repeat 10 times. Create a `00_setup.groovy` file and a `90_teardown.groovy` file in that same directory:

```
- tasks
  |- 00_demo
     |- 00_setup.groovy  (set taskIterations property to 10)
     |- 10_task_req.xml  (this is the XML request that will be sent to the server)
     |- 10_task_resp.xml  (this is the XML response that will be compared with the server response)
     |- 90_teardown.groovy  (set taskIterations property back to original value)
```

The contents of each of the two new files are as follows.

`00_setup.groovy`:

```
memory.oldTaskIterations = memory.taskIterations
memory.taskIterations = 10
```

`90_teardown.groovy`:

```
memory.taskIteration = memory.taskIterations // This line causes this groovy task to Iterate just once
memory.taskIterations = memory.oldTaskIterations
memory.oldTaskIterations = null
```

### Developer Setup

Install Ant and the Java Development Kit (JDK) and have both tools available in your shell `PATH`.

Run the following command line to build the project and run the unit tests: 

```
ant clean test
```

The test results summary will appear on the console but detailed results can be found in `gen/test/index.html`.

### Sample Tasks

The Toxic source tree includes a set of sample tasks for helping to illustrate how to use Toxic, and what kinds of capabilities it has.

#### Run Toxic against the included sample tests:

```
bin/toxic
```

Review the console output, the `results/toxic.xml` (jUnit format), and the sample tasks in the `test/samples` directory to familiarize yourself with this tool.

### Configure Toxic with custom, permanent memory variables

Toxic can be configured via a default property file or via a suite-specific property file. On of the many advantages found in Toxic is that the application is completely independent to the suites. The Toxic application has a `toxic.properties` file in the `conf` folder. An additional file (that overwrites variables in this file) is located in every folder of a suite or in the root folder of the suites. For this example, we will use the configuration file found in the suite instead of the project one. There you can specify the host that you will “hit” with the tests:

```
###############################################################################
# Default Property Overrides 
###############################################################################
httpHost=localhost
httpPort=6011
httpMethod=POST / HTTP/1.1
httpVerbose=true
...
```

### Using Toxic with Splunk
To use Toxic to add data such as log messages into Splunk, create request and response files similar to those shown below.  The toxic code has been modified so that the data field in the request is not url-encoded.

`example_req.splunk`

```
splunk_hostname=%splunkHost%
splunk_port=%splunkPort%
splunk_username=
splunk_password=
splunk_method=POST /services/receivers/simple
source=www
sourcetype=web_event
data="Aug 15 12:46:41 log message example"
```

`example_resp.splunk`

```
assert memory.lastResponse.contains("results")
return ''
```

Run toxic specifying the Splunk log in information to add data to Splunk.

```
bin/toxic -doDir=../toxic -splunkUser=admin -splunkPassword=admin -splunkHost=localhost -splunkPort=58089
```

### Using Toxic with SFTP

Toxic includes the JSch (Java Secure Channel) dependency to provide an easier abstraction over SFTP interactions. For most use cases, this is not needed, but for those requiring the conection to be estiblished using username and password it becomes really handy. Here's a sample to open and check a conection:

```
def checkSftpAccess = { host, user, pass ->
  JSch jsch = new JSch()
  jsch.setConfig("StrictHostKeyChecking", "no");
  Session session = jsch.getSession(user, host)
  session.setPassword(pass)
  try { 
    session.connect()
    def connected = session.isConnected()
    log.info("SFTP connected: $connected")
    return connected
  } catch(Exception e) {
    log.warn("Failed to connect to ${host}: ${e}")
    return false
  } finally {
    session.disconnect()
  }
}
```
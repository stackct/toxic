# Pickle

Pickle is a DSL (domain specific language) for composing integration tests using Toxic tasks.

## Concepts

At a high level, Pickle allows the composition of integration tests by grouping Toxic tasks into higher-order constructs that are simpler to write and reason about.

### Functions

Functions encapsulate a group of Toxic tasks that can run in isolation, and can optionally take arguments and return outputs to be used by subsequent Steps or in Assertions. Functions are defined with a `function` block inside a `.fn` file. Multiple functions can be defined within a single `.fn` file.

#### Fields

* **path** | **step(s)** (Required) - Path to the Toxic task(s) or a list of steps to execute
* **description** (Required) - A description of the Function
* **arg** | **input** (Optional) - Defines an input argument that can be marked as required or optional. Specified arguments are required by default. Multiple `arg` blocks are supported.
* **output** (Optional) - Defines an output that can be interpolated in subsequent Steps and/or Assertion statements (see [Interpolation](#interpolation)). Multiple `output` statements are supported.

```groovy
function "NAME" {
   path         "PATH_TO_LIB"
   description  "DESCRIPTION"

   arg "ARG1"                 // Arg1 is a required argument
   arg "ARG2", false          // Arg2 is an optional argument and if not specified, will NOT be set
   arg "ARG3", false, "foo"   // Arg3 is an optional argument and if not specified, will default to string foo

   output "OUTPUT1"
   output "OUTPUT2"
}
```

```groovy
function "NAME" {
   description  "DESCRIPTION"

   input "ARG1"

   step "FunctionName", "taskName", {
     foo '{{ ARG1 }}'
   }

   output "OUTPUT1", "{{ step.taskName.foo }}"
}
```

#### Targetted Functions

By default, functions must be unique within a namespace (see [Dependency Management](#dependency-management)). If a function does not specify any `targets` in its definition, it is considered _default_. Declaring multiple default implementations is not supported. However, it is possible to target overloaded implementations of a function. The following overloaded function definition is allowed, because the second definition targets a `legacy` value.

```
function "DoSomething" {
    description "Default implementation of DoSomething"
    libPath     "{{ libPath }}/dosomething"
}

function "DoSomething" {
    description "Legacy implementation of DoSomething"
    libPath     "{{ libPath }}/dosomething-legacy"

    targets "legacy"
}
```

To target the "legacy" implementation of `DoSomething`, specify `-target=legacy` at runtime. If no `-target` is specified at runtime, the default implementation will be used.


### Tests

Tests are composed of a series of Steps and an optional Assertion that determines whether the test passed or failed. Tests are defined in a `.test` file. Multiple tests can be defined within a single `.test` file.

Tests are composed of a collection of a `description`, an optional collection of `tags`, a collection of `task`s, and an `assertions` block. `Assertions` are evaluated after all tasks have been executed, although they can appear anywhere in the `test` definition. Steps are executed in the order in which they appear in the `test` definition.

#### Fields

* **description** (Required) - A description of the Test
* **tags** (Optional) - List of tags for optional filtering of tests at runtime
* **declare** (Optional) - Immutable variables that can be used within the test case as interpolated values
* **step** (Required) - Named invocation of a Function
   * *arg* (Optional) - Value for input argument as defined by the Function
* **assertions** (Optional) - Assertions to drive the pass/fail of a test case

```groovy
test "NAME" {
   description   "DESCRIPTION"
   tags          (["TAG1", "TAG2", ...])

   declare {
     KEY "VALUE"
   }

   step "FUNCTION_NAME", "TASK_NAME_1", {
      ARG1 "VALUE"
      ARG2 "VALUE"
   }

   step "FUNCTION_NAME", "TASK_NAME_2", {
      ARG1 "VALUE"
      ARG2 "VALUE"
   }

   assertions {
      [eq|neq|contains] "SHOULD CONTAIN THIS" "THIS"
   }
}
```

### Steps

Steps are named invocations of a Function, that allow for  the passing of input arguments to the Function, and the exporting of outputs from the Function to be used in subsequent Steps or Assertions (see [Interpolation](#interpolation)). Tests are composed of any number of sequential Steps.

Steps can be optionally run with a retry logic `wait` block. `wait` blocks require one or more `condition` blocks and the following arguments to be provided:

* `timeoutMs` - Time (in milliseconds) to retry before failing the Step
* `intervalMs` - Time (in milliseconds) between retries

```groovy
step "DoSomething", "do-it", {
    foo "bar"

    wait {
        intervalMs   10
        timeoutMs    30

        condition {
            eq "{{ status }}", "OK"
        }
    }
}
```

`condition` blocks contain one ore more matchers (see [Assertions](#assertions)). Multiple matchers within a single `condition` block are considered `AND` operations. To specify `OR` operations, use multiple `condition` blocks.

```groovy
step "DoSomething", "do-it", {
    foo "bar"

    wait {
        intervalMs   10
        timeoutMs    30

        condition {
            eq "{{ status }}", "OK"
            eq "{{ code }}", "200"
        } // OR

        condition {
            eq "{{ error }}", ""
        }
    }
}
```

### Assertions

Tests are concluded to have passed or failed, based on the statement contained within the Assertions block. Each statement in the Assertion block is composed using _matchers_. The supported matchers are:

* **eq** - Compares that two objects are equal. Usage `eq obj, obj`.
* **neq** - Compares that two objects are not equal. Usage `neq obj, obj`.
* **contains** - Compares that one object is contained within the other. In the case of a String, it works like a _substring_ match. Usage `contains obj, obj`. In the case of a Map, it will evaluate if the map contains a key.

## Interpolation

Pickle supports interpolation of values, using `"{{ var }}"` syntax, where `var` notation depends on the type of value to interpolate. Step outputs can be interpolated in Step argument values or Assertion statements.

### Supported Interpolation

* **Step Output** - If a Step invokes a Function that has an output defined, that output can be referenced using `"{{ step.STEP_NAME.OUTPUT }}"`. Step outputs and variables can be used in arg values for other Steps, or in Assertion statements.

   For example, given the following Function definitions:

   ```groovy
   function "create_user" {
      path "/path/to/lib/create_user"
      description "Creates a user"

      arg "name", true

      output "userId" // Returns library-generated identifier
   }

   function "login_user" {
      path "/path/to/lib/login_user"
      description "Logs a user in"

      arg "userId", true

      output "result" // Returns 'ok', or 'fail'
   }
   ```

   and Test definition:

   ```groovy
   test "A user can log in" {
      description "Proves that a user can log in"

      declare {
        user "Fred"
      }

      step "create_user", "fred", {
         name "{{ var.user }}"
      }

      step "login_user", "login", {
         userId "{{ step.fred.userId }}"
      }

      assertions {
         eq "ok", "{{ step.login.result }}"
      }
   }
   ```
* **Toxic Properties** - Toxic `memory` properties can be accessed directly, using `"{{ KEY }}"` syntax. For example, to interpolate `memory.userId`, use `"{{ userId }}"`.

## Composition

Function (`.fn`) and Test (`.test`) definition files can contain multiple blocks of their respective type, but not of each other. This allows for grouping of multiple related Functions within a single `.fn` file and multiple related Tests within a single `.test` file in order to better organize them. Test can be further related using Tags, which can then be reference when running Toxic to filter (see [Running Specific Tests](#running-specific-tests)).

## Dependency Management

Libraries and functions from other projects can be declared as dependencies and imported for use with local tests.

### Importing Libraries

Dependencies are declared using `.dep` files placed in a `deps` folder in the Toxic root (see [Directory Structure](#directory-structure)). `.dep` files can contain one or more dependency declarations and follow the following format:

```groovy
dep "ARTIFACT_NAME", "ALIAS (OPTIONAL)"
```

For example, to import a `create_order` function from a product named **ordersystem**, and refer to it as **order** within test cases, declare the dependency as follows:

```groovy
dep "ordersystem", "order"
```

Then, in a test case, refer to any functions from **ordersystem** as follows:

```groovy
test "A user can place an order" {
  description "Proves that a user can place an order"

  step "order.create_order", "simple-order", {
      amount   "1000"
      currency "USD"
  }

  assertions {
      eq "ok", "{{ step.simple-order.result }}"
  }
}
```

A version may be supplied within the artifact name to pin a test suite to a particular version.
The default and preferred usage is to always pull latest.
```groovy
// NOT a preferred usage
dep "ARTIFACT_NAME-1.0.0", "ALIAS (OPTIONAL)"
```

Pickle will attempt to resolve dependencies by appending a file extension suffix to the artifact name prior to resolving.
The default file extension is configured with the pickle.ext toxic property.
The dependency repo url is configured with the pickle.repoUrl property and optional basic auth is used when the pickle.repoUsername and pickle.repoPassword are supplied.
The deps resolver properties should be set in the local developers ${HOME}/.toxic/global.properties file
```
pickle.repoUrl=http://localhost:8081/repository/pickle
pickle.repoUsername=pickle
pickle.repoPassword=foo
```

To resolve a non-default package, the file extension can be supplied in the artifact name.
```groovy
dep "ARTIFACT_NAME.tgz", "ALIAS (OPTIONAL)"
```

Dependencies can be resolved to local directories by specifying the path to the dir when running Toxic:
```plain
toxic -deps.foo=/path/to/foo/toxic -doDir=toxic/tests
```

To disable dependency resolution and pull from cache, run Toxic with the following:
```plain
toxic -useDepsCache=true -doDir=toxic/tests
```

## Running Tests

An example of the suggested directory structure for integration tests is as follows:

### Directory Structure

```plain
toxic
├── deps
│   ├── external.dep
├── functions
│   ├── user.fn
├── library
│   ├── create_user
│   │   ├── 00_setup.properties
│   │   ├── 10_createUser_req.xml
│   │   ├── 10_createUser_resp.xml
│   │   └── 99_teardown.properties
│   ├── login_user
│   ├── rename_user
└── tests
    └── user.test
```

NOTE: To run Pickle tests, Toxic needs to compile the `.fn` files prior to executing any test. This is done by either linking (`.link`) directly to the directory containing the functions, or by setting a `fnDir` property.

Example:

```plain
toxic
├── functions
│   ├── user.fn
└── tests
    └── 00_compile_functions.link
    └── user.test
```

The cleaner way is to set the `fnDir` property either in the command-line:

```plain
toxic -fnDir=toxic/functions -doDir=toxic/tests
```

or, better yet, set the property permanently in a `.properties` file:

```plain
# Defaults
homePath=``memory.propertiesFile.parentFile.parentFile.canonicalPath``

# Function directory
fnDir=``memory.homePath``/functions
```

## Running Specific Tests

Adding Tags to a test allows Toxic to target specific tests at execution time. For example, given the following Tests:

```groovy
test "A user can log in" {
   description "Proves that a user can log in"

   // Tags as a List
   tags  "user", "user-essential"

   step "create_user", "fred", {
      name "Fred"
   }

   step "login_user", "login", {
      userId "{{ step.fred.userId }}"
   }

   assertions {
      eq "ok", "{{ step.login.result }}"
   }
}

test "A user can change his/her ID" {
   description "Proves that a user can change his/her ID"
   tags  "user", "user-additional"

   step "create_user", "fred", {
      name "Fred"
   }

   step "login_user", "login", {
      userId "{{ step.fred.userId }}"
   }

   step "rename_user", "rename", {
      userId "{{ step.fred.userId }}"
   }

   assertions {
      eq "ok", "{{ step.rename.result }}"
   }
}
```
### Positive Filtering

Tests can be positively filtered for by specifying the tags to include.

For example, to run all the 'user' tests:

```plain
toxic -doDir=toxic/tests -includeTags=user
```

To run only the _essential_ 'user' tests:

```plain
toxic -doDir=toxic/tests -includeTags=user-essential
```

### Negative Filtering

Tests can be negatively filtered for by specifying the tags to _exclude_ using the `-excludeTags` property.

For example, to run all tests _except_ the "user-additional" ones:

```plain
toxic -doDir=toxic/tests -excludeTags=user-additional
```

Positive and negative filtering can be combined. The example below will run all "user" tests, _except_ for the ones tagged as "user-additional":

```plain
toxic -doDir=toxic/tests -includeTags=user -excludeTags=user-additional
```

### Single Test Execution

Additionally, a single test can be run by specifying the `-test=TEST_NAME` argument.

```plain
toxic -doDir=toxic/tests -test=MyTest
```

If the single test name has spaces, escape with single and double quotes:

```plain
toxic -doDir=toxic/tests -test='"My Pickle Test"'
```

**NOTE: Specifying a single test case to run supercedes any positive or negative filtering.**

## Naming Strategy

The Pickle language is intended to be extremely reader-intuitive. Although primarily written by developers, the naming strategy should be framed for an audience of non-developers. Mixed casing across `Functions`, `Tests`, `Steps`, etc serves an intentional visual aid for readers to quickly discern between them. In all cases, shorter and better names are preferred.` 

### Functions
Function names should be in _TitleCase_, and begin with a verb.

Example: 
```
function "CreateUser" { ... }
```
is better than:
```
function "UserCreation" { ... }
```

### Tests

Test names should be short, meaningful statements that clearly reveal the intent. If it is too long to put on the cover of a book, its probably a bad test name. The test _description_ can contain further details about the intent of the test.

Example: 
```
test "Creates a Valid User" { ... }
    description "Proves that a user is created when the required data is valid"
}
```
is better than:
```
test "User Creation" { ... } // Too generic
```
or 
```
test "Create a user when valid data is provided" { ... } // Too verbose
```

### Steps
Step names should be as short and descriptive as possible, and named so that referring to the step in another step or in an assertion reads well. Single words are better than compound words. If compound words are required, _hyphen-case_ is preferred. The primary goal of the step name is to provide a readable expression when used as an interpolation.

Example:
The `CreateUser` function is responsible for creating a user and returning a `success` output indicating whether or not the operation succeeded.

```
test "Creates a Valid User", {
    step "CreateUser", "create", { ... }
    step "GetUserDetails", "user", { ...  }

   assertions {
        eq "{{ step.create.success }}", "1"
        eq "{{ step.user.}}
    }
}
```

In the above example:
```
step "CreateUser", "create", { ... }
```
is better than
```
step "CreateUser", "create-user", { ... }
```

because there is no other "create" step in this test, so there is no need to disambiguate. 

More examples:
```
step "GetProjectDetails", "project"
```
is better than
```
step "GetProjectDetails", "get-project-details"
```
because
```
{{ step.project.id }}
```
reads better than
```
{{ step.get-project-details.id }}
```

### Libraries
As libraries represent the low-level implementation of `Functions` as Toxic tasks, they are the closest to traditional programming paradigms. Library tasks are not intended for "public" reader consumption, and should be organized similar to a RESTful naming strategy. Files and directories should be in _camel_case_, and separated by subject/functional area.

For example:
```
function "CreateUser" {
    description "Creates a user with a default password"
    libPath     "{{ libPath }}/user/create"
}
```
is better than
```
function "CreateUser" {
    description "Creates a user with a default password"
    libPath     "{{ libPath }}/create_user"
}
```

This strategy allows for nesting related operations against a "user".

```
library/user/create
library/user/edit
library/user/delete
```
is better than
```
library/create_user
library/edit_user
library/delete_user
```

## Visual Studo Code Integration

Pickle provides code snippets to assist in authoring tests and functions within Visual Studio Code.

To install Pickle the code snippets, copy or symlink `resources/vscode/pickle/snippets/pickle.json` to your Visual Studio Code user preferences directory.
  * **Windows** - `%APPDATA%\Code\User\snippets`
  * **macOS** - `$HOME/Library/Application\ Support/Code/User/snippets`
  * **Linux** - `$HOME/.config/Code/User/snippets`

For example:

```
$ ln -s $(pwd)/resources/vscode/pickle/snippets/pickle.json` $HOME/Library/Application\ Support/Code/User/snippets/pickle.code-snippets
```

**NOTE** - Symlinks are preferred as changes to the source snippets will reflect automatically.

## What's Next

* Parallel test execution

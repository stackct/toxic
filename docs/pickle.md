# Pickle

Pickle is a DSL (domain specific language) for composing integration tests using Toxic tasks.

## Concepts

At a high level, Pickle allows the composition of integration tests by grouping Toxic tasks into higher-order constructs that are simpler to write and reason about.

### Functions

Functions encapsulate a group of Toxic tasks that can run in isolation, and can optionally take arguments and return outputs to be used by subsequent Steps or in Assertions. Functions are defined with a `function` block inside a `.fn` file. Multiple functions can be defined within a single `.fn` file.

#### Fields

* **path** (Required) - Path to the Toxic task(s)
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
      [eq|neq|contains] "SOMETHING", "OTHER"
   }
}
```

### Steps

Steps are named invocations of a Function, that allow for  the passing of input arguments to the Function, and the exporting of outputs from the Function to be used in subsequent Steps or Assertions (see [Interpolation](#interpolation)). Tests are composed of any number of sequential Steps.

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
      currency "USD
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
toxic -doDir=toxic/tests -test='"my pickle test"'
```

**NOTE: Specifying a single test case to run supercedes any positive or negative filtering.**

## What's Next

* Higher order function composition

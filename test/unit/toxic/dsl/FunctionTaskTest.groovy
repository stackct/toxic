package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class FunctionTaskTest {
  @Test
  void should_add_single_function_to_memory_map() {
    def memory = [:]
    doTask("""
        function "foo" {
          path "/path/to/lib"
          description "foo-description"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)

    def expectedFunction = new Function('foo')
    expectedFunction.path = '/path/to/lib'
    expectedFunction.args = [new Arg(name: 'arg1', required: true), new Arg(name: 'arg2', required: false)]
    expectedFunction.outputs = ['output1': null, 'output2': null]

    assert 1 == memory.functions.size()
    def actualFunction = memory.functions.foo
    assert expectedFunction.name == actualFunction.name
    assert expectedFunction.path == actualFunction.path
    assert expectedFunction.args.size() == actualFunction.args.size()
    assert expectedFunction.outputs == actualFunction.outputs
  }

  @Test
  void should_favor_targetted_fn() {
    def memory = [target:"bar"]
    doTask("""
        function "foo" {
          path "/path/to/lib"
          description "default"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)
    doTask("""
        function "foo" {
          path "/path/to/lib"
          description "override"
          targets "bar"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)


    assert 1 == memory.functions.size()
    assert memory.functions.foo.description == "override"
  }

  @Test
  void should_add_default_fn_when_target_not_matched() {
    def memory = [target:"bar"]
    doTask("""
        function "foo" {
          path "/path/to/lib"
          description "default"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)

    assert 1 == memory.functions.size()
  }

  @Test
  void should_not_add_fn_when_target_not_matched() {
    def memory = [target:"bar"]
    doTask("""
        function "foo" {
          path "/path/to/lib"
          description "default"
          targets "baz"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)

    assert 0 == memory.functions.size()
  }

  @Test
  void should_add_multiple_functions_to_memory_map() {
    def memory = [:]
    doTask("""
      function "foo" {
        path "/path/to/lib/foo"
        description "foo-description"
        
        arg "foo-arg1", true
        arg "foo-arg2", false
    
        output "foo-output1"
        output "foo-output2"
      }
      function "bar" {
        path "/path/to/lib/bar"
        description "bar-description"
        
        arg "bar-arg1", true
        arg "bar-arg2", false
    
        output "bar-output1"
        output "bar-output2"
      }""", memory)

    def expectedFoo = new Function('foo')
    expectedFoo.path = '/path/to/lib/foo'
    expectedFoo.args = [new Arg(name: 'foo-arg1', required: true), new Arg(name: 'foo-arg2', required: false)]
    expectedFoo.outputs = ['foo-output1': null, 'foo-output2': null]

    def expectedBar = new Function('bar')
    expectedBar.path = '/path/to/lib/bar'
    expectedBar.args = [new Arg(name: 'bar-arg1', required: true), new Arg(name: 'bar-arg2', required: false)]
    expectedBar.outputs = ['bar-output1': null, 'bar-output2': null]

    assert 2 == memory.functions.size()
    def actualFoo = memory.functions.foo
    assert expectedFoo.name == actualFoo.name
    assert expectedFoo.path == actualFoo.path
    assert expectedFoo.args.size() == actualFoo.args.size()
    assert expectedFoo.outputs == actualFoo.outputs

    def actualBar = memory.functions.bar
    assert expectedBar.name == actualBar.name
    assert expectedBar.path == actualBar.path
    assert expectedBar.args.size() == actualBar.args.size()
    assert expectedBar.outputs == actualBar.outputs
  }

  @Test
  void should_interpolate_function_path() {
    def memory = [libPath:'/path/to/lib']
    doTask("""
        function "foo" {
          path "{{ libPath }}"
          description "foo-description"
          
          arg "arg1", true
          arg "arg2", false
      
          output "output1"
          output "output2"
        }""", memory)

    def expectedFunction = new Function('foo')
    expectedFunction.path = '/path/to/lib'
    expectedFunction.args = [new Arg(name: 'arg1', required: true), new Arg(name: 'arg2', required: false)]
    expectedFunction.outputs = ['output1': null, 'output2': null]

    assert 1 == memory.functions.size()
    def actualFunction = memory.functions.foo
    assert expectedFunction.name == actualFunction.name
    assert expectedFunction.path == actualFunction.path
    assert expectedFunction.args.size() == actualFunction.args.size()
    assert expectedFunction.outputs == actualFunction.outputs
  }

  @Test
  void should_namespace_external_functions() {
    def memory = [libPath: '/tmp/lib']
    doTaskFromDep("""
        function "foo" {
          path "{{ libPath }}"
          description "foo-description"
        }""", memory)

    assert '/tmp/lib' == memory.libPath
    assert 1 == memory.functions.size()
    def actualFunction = memory.functions['toxic.foo']
    assert 'foo' == actualFunction.name
    assert new File(memory.deps.toxic, 'library').canonicalPath == actualFunction.path
  }

  @Test
  void should_fail_to_add_duplicated_function_by_name() {
     try {
      doTask("""
        function "foo" {
          path "/path/to/lib"
          description "foo-description"
          arg "arg1", true
          output "output1"
        }
        function "foo" {
          path "/path/to/lib"
          description "foo-description"
          arg "arg1", true
          output "output1"
        }""", [:])
      fail('Expected IllegalArgumentException')
    }
    catch(IllegalArgumentException e) {
      assert 'Found duplicated function name; name=foo' == e.message
    }
  }

  @Test
  void should_fail_to_add_duplicated_dep_function_by_name() {
    try {
      doTaskFromDep("""
      function "foo" {
        path "{{ libPath }}"
        description "foo-description"
      }
      function "foo" {
        path "{{ libPath }}"
        description "foo-description"
      }""", [:])

      fail('Expected IllegalArgumentException')
    }
    catch(IllegalArgumentException e) {
      assert 'Found duplicated function name; name=toxic.foo' == e.message
    }
  }

  @Test
  void should_allow_for_local_fn_and_dep_fn_to_share_names() {
    def memory = [:]

    doTask("""
      function "foo" {
        path "/path/to/lib"
        description "foo-description"
        arg "arg1", true
        output "output1"
      }""", memory)

    doTaskFromDep("""
      function "foo" {
        path "{{ libPath }}"
        description "foo-description"
      }""", memory)

    assert 2 == memory.functions.size()
    def localFunction = memory.functions['foo']
    assert 'foo' == localFunction.name
    assert '/path/to/lib' == localFunction.path

    def depFunction = memory.functions['toxic.foo']
    assert 'foo' == depFunction.name
    assert new File(memory.deps.toxic, 'library').canonicalPath == depFunction.path
  }

  def doTask(String fileText, def memory) {
    def task = new FunctionTask()
    def file
    try {
      file = File.createTempFile('foo', '.fn')
      file.text = fileText

      task.init(file, [:])
      task.doTask(memory)
    }
    finally {
      file?.delete()
    }
  }

  def doTaskFromDep(String fileText, def memory) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      File depsDir = new File(tempDir, 'deps/toxic')
      File fnDir = new File(depsDir, 'functions')
      fnDir.mkdirs()
      File fnFile = new File(fnDir, 'foo.fn')
      fnFile.text = fileText

      def task = new FunctionTask()
      task.init(fnFile, [:])

      memory.deps = [toxic:depsDir]
      task.doTask(memory)
    }
    finally {
      tempDir?.deleteDir()
    }
  }
}

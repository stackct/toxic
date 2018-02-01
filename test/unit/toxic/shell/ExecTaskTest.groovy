// Copyright (c) 2015 Value Pay Services, LLC.  All rights reserved.
package toxic.shell

import org.junit.*
import toxic.ToxicProperties

public class ExecTaskTest {
  @Test
  public void test_simple_exec() {
    def execTask = new ExecTask()
    def input = "echo hello"
    def result = execTask.transmit(input, [:])
    assert result.toString().trim() == "hello"
  }

  @Test
  public void test_complex_exec() {
    def execTask = new ExecTask()
    def input = """exec.cmd=echo
                   exec.arg.1=hello
                   exec.arg.6=you
                   exec.arg.3=fella
                   exec.arg.2=there
                   exec.arg.4=how
                   exec.arg.5=are
                   """
    def result = execTask.transmit(input, [:])
    assert result.toString().trim() == "hello there fella how are you"
  }

  @Test
  public void test_no_timeout_exec() {
    def execTask = new ExecTask()
    def input = """exec.cmd=sleep
                   exec.arg.1=2
                   """
    def startTime = System.currentTimeMillis()
    def result = execTask.transmit(input, [:])
    assert System.currentTimeMillis() - startTime > 2000
  }
  
  @Test
  public void test_timeout_exec() {
    def execTask = new ExecTask()
    def input = """exec.cmd=sleep
                   exec.arg.1=5
                   exec.timeoutSecs=1
                   """
    def startTime = System.currentTimeMillis()
    def result = execTask.transmit(input, [:])
    assert System.currentTimeMillis() - startTime < 5000
  }
  
  @Test
  public void test_failed_exec() {
    def execTask = new ExecTask()
    def input = "ls hello"
    def result = execTask.transmit(input, [:])
    assert result.toString().contains("exitCode=")
    assert result.toString().contains("stderr=")
    assert result.toString().contains("stdout=")
  }
}

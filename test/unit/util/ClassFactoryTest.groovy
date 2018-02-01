package util

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

import log.Log

class ClassFactoryTest {

  @Before
  void before() {
    ClassFactory.reset()
  }

  @Test
  void should_construct_with_blank_suffix() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefixSuffix", "")

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "No Arg Constructor"
  }

  @Test
  void should_construct_without_arguments() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefix", "Suffix")

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "No Arg Constructor"
  }

  @Test
  void should_construct_with_hash_arguments() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefix", "Suffix", [text: "Init From Hash"])

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "Init From Hash"
  }

  @Test
  void should_construct_with_arguments() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefix", "Suffix", "String Constructor")

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "String Constructor"
  }

  @Test
  void should_construct_with_arguments_array() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefix", "Suffix", ["String Constructor"] as String[])

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "String Constructor"
  }

  @Test
  void should_construct_with_arguments_list() {
    def instance = ClassFactory.factory("util.ClassFactoryTestPrefix", "Suffix", ["String Constructor"])

    assert instance instanceof ClassFactoryTestPrefixSuffix
    assert instance.text == "String Constructor"
  }

  @Test
  void should_throw_if_the_class_isnt_found_and_log_full_stack_trace_by_default() {
    checkLogging(true, "Dynamic class construction failed: unknownPrefixUnknownSuffix")
    checkLogging(true, "java.lang.ClassNotFoundException: unknownPrefixUnknownSuffix")
  }

  @Test
  void should_throw_if_the_class_isnt_found_and_log_full_stack_trace() {
    ClassFactory.logFull()

    checkLogging(true, "Dynamic class construction failed: unknownPrefixUnknownSuffix")
    checkLogging(true, "java.lang.ClassNotFoundException: unknownPrefixUnknownSuffix")
  }

  @Test
  void should_throw_if_the_class_isnt_found_and_log_simple_log_line() {
    ClassFactory.logLine()

    checkLogging(true, "Dynamic class construction failed: unknownPrefixUnknownSuffix")
    checkLogging(false, "java.lang.ClassNotFoundException: unknownPrefixUnknownSuffix")
  }

  @Test
  void should_throw_if_the_class_isnt_found_and_log_nothing() {
    ClassFactory.logNone()
    
    checkLogging(false, "Dynamic class construction failed: unknownPrefixUnknownSuffix")
    checkLogging(false, "java.lang.ClassNotFoundException: unknownPrefixUnknownSuffix")
  }

  private checkLogging(expectedLog, expectedMessage) {
    def text = Log.capture(ClassFactory.log) {
      try {
        ClassFactory.factory("unknownPrefix", "unknownSuffix")
        fail "should have thrown ClassNotFoundException"
      }
      catch (ClassNotFoundException e) {
        // Success
      }
    }

    assert expectedLog == text.contains(expectedMessage)
  }
}

class ClassFactoryTestPrefixSuffix {
  def text

  def ClassFactoryTestPrefixSuffix() {
    this.text = "No Arg Constructor"
  }

  def ClassFactoryTestPrefixSuffix(HashMap args) {
    this.text = args.text
  }

  def ClassFactoryTestPrefixSuffix(String text) {
    this.text = text
  }
}



package toxic

import toxic.ToxicProperties
import org.apache.log4j.Logger

public abstract class CompareTask extends Task {
  protected static Logger slog = Logger.getLogger(CompareTask.class.name)
  def reqContent

  protected void initFile(File input) {
    if (!(input instanceof File) || !input.exists() || input.isDirectory()) {
      throw new IllegalArgumentException("Specified input is not a valid file")
    }

    if (!input.name.contains("_req.")) {
      throw new IllegalArgumentException("Specified input filename does not conform to the required '*_req.*' naming convention")
    }

    reqContent = input.getText("UTF-8")
  }

  public void init(def input, def props) {
    super.init(input, props)

    if (input instanceof File) {
      initFile(input)
    } else {
      reqContent = input
    }
  }

  public List<TaskResult> doTask(def memory) {
    def request = prepare(reqContent)
    memory.lastResponse = transmit(request, memory)
    if (input instanceof File) {
      def expected = lookupExpectedResponse(input)
      validate(memory.lastResponse, expected, memory)
    }
    return null
  }

  def prepare(def request) {
    return replace(request)
  }

  protected abstract transmit(request, memory)

  protected String lookupExpectedResponse(File srcFile) {
    def expected
    def filename = srcFile.canonicalPath.replaceFirst("_req\\.", "_resp\\.")
    def f = new File(filename)
    if (f.exists()) {
      expected = replace(f.text)
    }
    return expected
  }

  protected static String callReplacer(def classProperty, def input, def props) {
    def result
    def c = props.resolveClass(classProperty)
    if (c == null) {
      throw new IllegalArgumentException("Missing classname for property; classProperty=" + classProperty)
    }
    def replacer = c.newInstance()
    replacer.init(props)
    result = replacer.replace(input)
    return result
  }

  public static String replace(def str, def props) {
    def result = str
    props?.keySet()?.sort()?.each { key ->
      if (key.startsWith("task.replacer.")) {
        result = callReplacer(key, result, props)
      }
    }
    return result
  }

  protected String replace(def str) {
    return replace(str, props)
  }

  protected String callValidator(def classProperty, def actual, def expected, ToxicProperties memory) {
    try {
      def c = memory.resolveClass(classProperty)
      if (c == null) {
        throw new IllegalArgumentException("Missing classname for property; classProperty=" + classProperty)
      }
      def validator = c.newInstance()
      validator.init(memory)
      validator.validate(actual, expected, memory)
    } catch (Exception e) {
      log.error("Validator error; validatorProp=" + classProperty + "; actual=" + actual + "; expected=" + expected)
      throw e
    }
  }

  protected void validate(String test, String control, ToxicProperties memory) {
    props?.keySet().sort().each { key ->
      if (key.startsWith("task.validator.")) {
        callValidator(key, test, control, memory)
      }
    }
  }
}

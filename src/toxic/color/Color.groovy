package toxic.color

public class Color {
  
  public static String success(ConfigObject memory, String message) {
    def requiredColor = memory.colorSuccess ?= "32"
    return toColor(memory.colorEnabled, memory.colorSuccess.toString(), message)
  }

  public static String failure(LinkedHashMap memory, String message) {
    def requiredColor = memory.colorFailure ?= "31"
    return toColor(memory.colorEnabled, memory.colorFailure.toString(), message)
  }

  public static String failure(ConfigObject memory, String message) {
    def requiredColor = memory.colorFailure ?= "31"
    return toColor(memory.colorEnabled, memory.colorFailure.toString(), message)
  }

  public static String step(ConfigObject memory, String message) {
    return toColor(memory.colorEnabled, "32", message)
  }

  public static String http(ConfigObject memory, String message) {
    def requiredColor = memory.colorHttp ?= "25"
    return toColor(memory.colorEnabled, requiredColor.toString(), message)
  }

  public static String sql(ConfigObject memory, String message) {
    def requiredColor = memory.colorSql ?= "38"
    return toColor(memory.colorEnabled, requiredColor.toString(), message)
  }
  
  private static String toColor(Object enabled, String color, String message) {
    def isEnabled = true
    if (enabled instanceof Boolean) isEnabled = enabled
    if (enabled instanceof String) isEnabled = enabled.toBoolean()

    return isEnabled ? "\u001B[38;5;${color}m${message}\u001B[0m" : message
  }
}


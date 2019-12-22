package toxic.slack.command

import toxic.slack.*

import com.google.common.reflect.ClassPath

class CommandFactory {

  private static List<String> commands = []

  public static Command make(String cmd, SlackHandler handler) {
    try {
      Class.forName("toxic.slack.command.${cmd.capitalize()}Command").newInstance(handler)
    } catch (ClassNotFoundException cnf) {
      throw new InvalidCommand("Command is not valid: ${cmd}")
    }
  }

  public static List available() {
    if (!commands) {
      /*
       * This is horribly memory and cpu inefficient, and time consuming. Just hardcode the classes we support.
       *
      def clazz = CommandFactory.class

      commands = ClassPath.from(clazz.classLoader)
        .getTopLevelClasses(clazz.package.name)
        .findAll { c -> include(c) }
        .collect { c -> (c.simpleName - "Command").toLowerCase() }
      */

      commands = [
       "about",
       "ack",
       "assign",
       "bounce",
       "describe",
       "halt",
       "latest",
       "list",
       "log",
       "oncall",
       "pause",
       "perform",
       "resolve",
       "run",
       "silence",
       "unpause"
      ]
    }

    commands
  }

  private static boolean include(def c) {
    def clazz = c.load()
    def exclude = [ CommandFactory.class, BaseCommand.class, InvalidCommand.class ]

    !(clazz.isInterface()) &&
    !(clazz in exclude) &&
    !(c.simpleName.endsWith("Test"))
  }
}

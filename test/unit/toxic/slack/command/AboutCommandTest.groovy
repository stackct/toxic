package toxic.slack.command

import org.junit.*

import toxic.*

public class AboutCommandTest extends CommandTest {

@After
void after() {
  Environment.instance.metaClass = null
}

@Test
  void should_include_about_info() {
    Environment.instance.metaClass.toSimple = { -> [heapUsed:100F, heapMax:200F] }

    new AboutCommand(sh).handle([], bot, ".about").with { about ->
      assert about.contains("Version");
      assert about.contains("Server URL")
      assert about.contains("Operating System")
      assert about.contains("CPUs")
      assert about.contains("Load")
      assert about.contains("Memory (Heap)")
      assert about.contains("Shutdown Pending")
      assert about.contains("Running Jobs")
    }
  }
}
package toxic.dsl

class StepFile extends File {
  StepFile(String name) {
    super(name)
  }

  StepFile(File parent, String name) {
    super(parent, name)
  }

  StepFile(java.net.URI uri) {
    super(uri)
  }

  void complete(props) {
    TestCaseHandler.stepComplete(props)
  }
}

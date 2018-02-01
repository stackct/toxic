package toxic.dsl

class Function extends Parser {
  private static final List<String> requiredFields = ['name', 'path', 'description']

  String name
  String path
  String description
  List<Arg> args = []
  Set<String> outputs = []

  Function() {}

  Function(String name) {
    this.name = name
  }

  static def parse(String input) {
    parse(new Function(), input)
  }

  void function(String name, Closure body) {
    def fn = new Function(name)
    body.setResolveStrategy(Closure.DELEGATE_FIRST)
    body.delegate = fn
    body()
    fn.validate()
    results << fn
  }

  def path(String path) {
    this.path = path
  }

  def description(String description) {
    this.description = description
  }

  def arg(String name, Boolean required=false) {
    args << new Arg(name: name, required: required)
  }

  def output(String key) {
    outputs << key
  }

  void validate() {
    def missingFields = []
    requiredFields.each { field ->
      if(!this."${field}") { missingFields << field }
    }
    if(missingFields) {
      throw new IllegalStateException("Missing required fields for function; name=${name}; fields=${missingFields}")
    }
  }

  void validateRequiredArgsArePresent(def props) {
    def missingArgs = []
    args.each {
      if(it.required && !props.containsKey(it.name)) {
        missingArgs << it.name
      }
    }
    if(missingArgs) {
      throw new IllegalStateException("Missing required args for function; name=${name}; args=${missingArgs}")
    }
  }

  void validateArgIsDefined(String argName) {
    Arg arg = args.find { it.name == argName }
    if(!arg) {
      throw new IllegalStateException("Arg is not defined for function; name=${name}; arg=${argName}")
    }
  }

  @Override
  String getKeyword() { 'function' }

  @Override
  String toString() {
    this.name + "(" +  args.collect { it.name }.join(',') + ")"
  }
}

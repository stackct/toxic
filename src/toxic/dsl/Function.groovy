package toxic.dsl

class Function extends StepParser {
  private static final List<String> requiredFields = ['name', 'description']

  String name
  String path
  String description
  List<Arg> args = []
  Set<String> targets = [] as Set
  Map<String, Object> outputs = [:]

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

  def targets(String... targets) {
    targets.each { t ->
      this.targets << t.trim()
    }
  }

  def path(String path) {
    this.path = path
  }

  def description(String description) {
    this.description = description
  }

  def arg(String name, boolean required=true) {
    addArg(name, required, false)
  }

  def arg(String name, boolean required, def defaultValue) {
    addArg(name, required, true, defaultValue)
  }

  def input(String name, boolean required=true) {
    addArg(name, required, false)
  }

  def input(String name, boolean required, def defaultValue) {
    addArg(name, required, true, defaultValue)
  }

  void addArg(String name, boolean required, boolean hasDefaultValue, def defaultValue = null) {
    if(required && hasDefaultValue) {
      throw new IllegalStateException("Cannot specify a default value on a required arg; name=${this.name}; args=${name}; defaultValue=${defaultValue}")
    }
    args << new Arg(name: name, required: required, hasDefaultValue: hasDefaultValue, defaultValue: defaultValue)
  }

  def output(String key, String value = null) {
    outputs[key] = value
  }

  void validate() {
    def missingFields = []
    requiredFields.each { field ->
      if(!this."${field}") { missingFields << field }
    }
    if(!path && !steps) {
      missingFields << '(path OR steps)'
    }
    if(name?.contains('.')) {
      throw new IllegalArgumentException("Function name cannot contain dots; name=${name}")
    }
    if(missingFields) {
      throw new IllegalStateException("Missing required fields for function; name=${name}; fields=${missingFields}")
    }
    if(path && steps) {
      throw new IllegalStateException("Function cannot specify both path and steps; name=${name}")
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

  public boolean hasTarget(String t) {
    if (!t) return true
    this.targets.contains(t)
  }

  @Override
  String getKeyword() { 'function' }

  @Override
  String toString() {
    this.name + "(" +  args.collect { it.name }.join(',') + ")"
  }
}

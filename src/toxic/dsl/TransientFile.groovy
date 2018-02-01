package toxic.dsl

class TransientFile extends File {
  String contents
  Closure resolver

  TransientFile(File parent, String child, String contents, Closure resolver = {String c -> c}) {
    super(parent, child)
    this.contents = contents
    this.resolver = resolver
  }

  String getText() { resolver(contents) }

  @Override
  boolean exists() { true }

  @Override
  boolean isDirectory() { false }
}

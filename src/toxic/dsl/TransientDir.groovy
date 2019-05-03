package toxic.dsl

class TransientDir extends File {
  File file

  TransientDir(File file) {
    super(file.parent)
    this.file = file
  }

  @Override
  boolean exists() { true }

  @Override
  boolean isDirectory() { true }

  @Override
  File[] listFiles() { [file] }
}

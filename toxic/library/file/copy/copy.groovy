memory.path = java.nio.file.Paths.get(memory.dest)
java.nio.file.Files.copy(java.nio.file.Paths.get(memory.src), memory.path)
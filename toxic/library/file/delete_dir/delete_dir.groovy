if(memory.preserveOutput) {
  log.info("Skipping output cleanup: ${memory.path.toString()}")
  return
}
new File("${memory.path}").deleteDir()
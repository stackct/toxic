memory.value = []
new File(memory.dir).eachFile {
  memory.value << it
}
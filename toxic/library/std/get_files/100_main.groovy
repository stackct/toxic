memory.value = []
new File(memory.dir).eachFile {
  if( !memory.pattern || it.name.matches(memory.pattern) ) memory.value << it
}
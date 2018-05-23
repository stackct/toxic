/*
If 'force' is set to true, overwrite any previous values. If 'force' is set to 'false',
only set the value if it is not already set.
*/
def key = memory['key']
def val = memory['value']
def force = (memory.force?.toString().equalsIgnoreCase("true"))

if (memory.containsKey(key) && !force) {
  println "property '${key}' is already set; use 'force=true' to overwrite"
  return
}

memory[key] = val
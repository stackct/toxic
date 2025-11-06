import groovy.json.*

def cmd = []
cmd << "curl"
cmd << "-XPOST"
cmd << "-sk"

def fields = memory.fields.split(",")
def values = memory.values.split(",")

if (fields.size() != values.size()) {
  throw new Exception("fields and values are not aligned")
}

for (i=0; i<fields.size(); i++) {
  cmd << "-F"
  cmd << "${fields[i]}=${values[i]}"
}

if (memory.compressed == "true") {
  cmd << "--compressed"
}

if (memory.bearer) {
  cmd << "--oauth2-bearer"
  cmd << "${memory.bearer}"
}

if (memory.cookie) {
  cmd << "--cookie"
  cmd << "${memory.cookie}"
}

cmd << memory.url

execWithEnvNoLogging(cmd)

memory.response = getOutputBuffer().toString()

try {
  memory.json = new JsonSlurper().parseText(memory.response)
}
catch(e) {
  log.warn("response body did not contain valid JSON; json output will not be set")
}



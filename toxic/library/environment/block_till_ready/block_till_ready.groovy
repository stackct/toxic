memory.ready = false

def url = new URL("${memory.url}")

def attempts = 0

while (!memory.ready && attempts++ < memory.attempts) {
  
  def connection = url.openConnection()
  connection.requestMethod = "GET"
  
  try {
    memory.ready = (connection.responseCode == 200)
  }
  catch(Exception e) { }
  
  if (!memory.ready) {
    sleep(memory.intervalMs)
  }
}
if (!memory.ready) {
  assert false : "host did not go ready in time, url=${memory.url} attempts=${memory.attempts}, intervalMs=${memory.intervalMs}, totalSeconds=${(memory.attempts * memory.intervalMs)/1000}"
}

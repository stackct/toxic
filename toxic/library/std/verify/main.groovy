import util.Wait

def verifyUrl = { url, waitMs ->
  def closure = { 
    localUrl -> execWithEnv(['curl', '-s', '-o', '/dev/null', '-L', '-k', '-w', "%{http_code}", localUrl]) 
    return out.toString() in ['200']
  }
  if (waitMs != null) {
    def ok = false
    Wait.on { -> 
      ok = closure(url)
      return ok
    }.every(1000).atMostMs(waitMs).start()
    return ok
  } else {
    return closure(url)
  }
}

def input = memory['url'] // String or List
def waitMs = memory['waitMs'] // int, millis to wait for success

if (input instanceof String) {
  memory['result'] = verifyUrl(input, waitMs) ? 'ok' : 'fail'
}

if (input instanceof List) {
  memory['result'] = input.every { url -> verifyUrl(url, waitMs) } ? 'ok' : 'fail'
}

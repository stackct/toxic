import util.Wait

def verifyUrl = { url, waitMs ->
  def closure = { 
    localUrl -> execWithEnv(['curl', '-s', '-o', '/dev/null', '-L', '-k', '-w', "%{http_code}", localUrl], [:], 5) 
    return out.toString()
  }
  if (waitMs != null) {
    def ok = false
    Wait.on { -> 
      ok = closure(url)
      return ok
    }.every(1000).atMostMs(waitMs).start()
    return ok in ['200']
  } else {
    return closure(url) in ['200']
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

memory.httpUri = memory.redirectHttpUri
memory.httpMethod = memory.redirectHttpMethod

if (memory.authCookie) {
  memory["http.header.Cookie"] = memory.authCookie
}

if (memory.cookies) {
  def cookies = memory.cookies.collect { name, value ->
    "${name}=${value}"
  }.join("; ")
  memory["http.header.Cookie"] = cookies
}

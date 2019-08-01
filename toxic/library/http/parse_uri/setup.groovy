def uri = new URI(memory.uri)
memory.path = uri.path
memory.scheme = uri.scheme
memory.host = uri.host
memory.port = uri.port
memory.queryParams = toxic.http.HttpTask.getQueryParams(uri)
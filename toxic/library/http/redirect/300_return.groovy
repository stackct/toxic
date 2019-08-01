memory.redirectUrl = memory['http.response.headers']['Location'] ?: memory['http.response.headers']['location']
memory.redirectHttpUri = memory['http.response.location']["httpUri"]
memory.redirectHttpMethod = memory['http.response.location']['httpMethod']
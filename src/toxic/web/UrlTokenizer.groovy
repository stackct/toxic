package toxic.web

@Singleton
public class UrlTokenizer {

  private static int ID_LENGTH = 6
  private Map registry = [:]        // [token: long-url]

  public get(String token) {
    if (!registry.containsKey(token))
      throw new UrlTokenNotFound("Could not locate '${token}'")

    registry[token]
  }

  protected void clear() {
    registry = [:]
  }

  protected String tokenize(String url) {
    def r = new Random()
    def sb = new StringBuffer()

    ID_LENGTH.times { n ->
      sb.append(Integer.toHexString(r.nextInt()))
    }

    sb.toString()[0..ID_LENGTH].with { id ->
      if (!registry.containsKey(id)) 
        registry[id] = url
        return id
      
      nextId(url)
    }
  }
}


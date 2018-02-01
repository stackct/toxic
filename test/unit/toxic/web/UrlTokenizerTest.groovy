package toxic.web

import org.junit.*

public class UrlTokenizerTest {

  def shortener

  @Before
  void before() {
    shortener = UrlTokenizer.instance
    shortener.clear()
  }

  @Test
  void should_return_unique_token_for_each_entry() {
    def url = "my-really-ugly-url"
    assert shortener.tokenize(url) != shortener.tokenize(url)
  }

  @Test(expected=UrlTokenNotFound)
  void should_throw_exception_if_token_not_found() {
    shortener.get("DOESNT_EXIST")
  }

  @Test
  void should_return_shortened_url_from_token() {
    shortener.registry = ["abcdef": "http://some-long-url"]

    assert shortener.get("abcdef")  == "http://some-long-url"
  }
}
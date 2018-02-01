package toxic.dsl

import org.junit.Test
import static org.junit.Assert.fail

class LibTest {
  
  @Test
  void should_parse_single_lib_statement() {
    def input =  { ->
      lib "https://foo.url"
    }

    Parser.parse(new Lib(), input).with { libs ->
      assert 1 == libs.size()
      assert libs[0] == 'https://foo.url'
    }
  }

  @Test
  void should_parse_multiple_lib_statements() {
    def input =  { ->
      lib "https://foo.url"
      lib "https://bar.url"
    }

    Parser.parse(new Lib(), input).with { libs ->
      assert 2 == libs.size()
      assert libs[0] == 'https://foo.url'
      assert libs[1] == 'https://bar.url'
    }
  }

  @Test
  void should_parse_multiple_lib_statements_from_string() {
    def input = """
      lib "https://foo.url"
      lib "https://bar.url"
      """

    Lib.parse(input).with { libs ->
      assert 2 == libs.size()
      assert libs[0] == 'https://foo.url'
      assert libs[1] == 'https://bar.url'
    }
  }
}

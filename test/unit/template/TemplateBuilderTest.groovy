package template;

import org.junit.*
import org.junit.rules.*
import groovy.mock.interceptor.*
import static org.junit.Assert.*

class TemplateBuilderTest {
  @org.junit.BeforeClass static void beforeClass() { log.Log.configureSimpleLogging() }
  int loadedFromDiskCount = 0
  boolean isFound = true
  
  @Before
  void before() {
    TemplateBuilder.singleton = null
    setupTemplates()
  }
  
  def setupTemplates() {
    TemplateBuilder.instance.config.template = [path: "/tmp/templates"]
    File.metaClass.constructor = { String parent, String child -> 
      loadedFromDiskCount++
      assert parent == "/tmp/templates"
      return new File(parent, child) {
        boolean isDirectory() { 
          child in ["template1", "template2"] 
        }

        Object eachFile(Closure closure) {
          closure(new File("template.info") { 
            BufferedInputStream newInputStream() {
              def content = """
                id=t1
                fromName=Big Corp
                fromEmail=user@host.domain
                replyToEmail=reply@here.domain
                subject=Test
                property1=value1
              """
              new BufferedInputStream(new StringBufferInputStream(content))
            }
          })

          closure(new File("text.html") {
            String getText() { 
              "<html>hello %%user%%</html>"
            }
          })

          closure(new File("text.plain") { 
            String getText() { 
              "goodbye\n%%first%%\n%%last%%"
            }
          })

          closure(new File("text.plain") { 
            String getText() {
              "hello %%first%% %%last%%, goodbye\n%%first%%\n%%last%%"
            }
          })

          closure(new File("text.plain") { 
            String getText() {
              "property1=%%property1%%"
            }
          })
        }
      }
    }    
  }
  
  @After
  void after() {
    File.metaClass = null
    TemplateBuilder.singleton = null
  }
  
  @Test
  void should_load_and_cache() {
    assert !TemplateBuilder.instance.cache
    assert loadedFromDiskCount == 0
    def template1a = TemplateBuilder.instance.build("template1")
    assert TemplateBuilder.instance.cache.size() == 1
    assert loadedFromDiskCount == 1
    def template1b = TemplateBuilder.instance.build("template1")
    assert TemplateBuilder.instance.cache.size() == 1
    assert loadedFromDiskCount == 1
    def template2 = TemplateBuilder.instance.build("template2")
    assert TemplateBuilder.instance.cache.size() == 2
    assert loadedFromDiskCount == 2
    
    assert template1a.is(template1b)
    assert !template1a.is(template2)
    assert TemplateBuilder.instance.cache.template1 == template1a
    assert TemplateBuilder.instance.cache.template1 == template1b
    assert TemplateBuilder.instance.cache.template2 == template2
  }

  @Test(expected=TemplateException)
  void should_throw_exception_if_template_not_found() {
    TemplateBuilder.instance.load("something missing")
  }
  
  @Test
  void should_load_template_from_file() {
    def template = TemplateBuilder.instance.load("template1")

    assert template.id == "t1"
    assert template.contents.size() == 4
    assert template.contents[0].type == "text/html; charset=utf-8"
    assert template.contents[1].type == "text/plain; charset=utf-8"
    assert template.contents[2].type == "text/plain; charset=utf-8"
  }

  @Test
  void should_add_values_into_template() {
    def builder = TemplateBuilder.instance
    def template = builder.load("template1")

    assert builder.personalize(template.contents[0].content,['user':'jsmith']) == "<html>hello jsmith</html>"
    assert builder.personalize(template.contents[1].content,['first':'jack','last':'smith']) == "goodbye\njack\nsmith"
    assert builder.personalize(template.contents[1].content,['first':'jack','last':null]) == "goodbye\njack\n"
    assert builder.personalize(template.contents[2].content,['first':'jack S\\','last':'\\S \$S']) == "hello jack S\\ \\S \$S, goodbye\njack S\\\n\\S \$S"
  }
  
  @Test
  void should_merge_info_attributes_into_user_supplied_attributes() {
    def builder = TemplateBuilder.instance
    def template = builder.load("template1")

    assert builder.personalize(template.contents[3].content, [:], ["property1":"value1"]) == "property1=value1"
  }

  @Test
  void should_load_config() {
    assert TemplateBuilder.instance.config.size() == 1
    assert TemplateBuilder.instance.config.template.path == "/tmp/templates"

    TemplateBuilder.instance.config.clear()
  }

  @Test
  void should_be_able_to_verify_if_a_template_exists_in_cache() {
    def builder = TemplateBuilder.instance
    def template = builder.load("template1")

    assert builder.exists("template1")
    assert !builder.exists("INVALID_TEMPLATE_ID")
  }

  @Test
  void should_be_able_to_verify_if_a_template_exists_on_disk_before_cache_load() {
    def builder = TemplateBuilder.instance

    assert builder.exists("template1")
  }

  @Test
  void should_reset_the_statics() {
    def original = TemplateBuilder.instance
    def originalCache =  TemplateBuilder.instance.cache

    TemplateBuilder.reset()

    assert !TemplateBuilder.instance.is(original)
    assert !TemplateBuilder.instance.cache.is(originalCache)
  }

  @Test
  void should_mock_itself() {
    def mock = TemplateBuilder.mock()

    mock.use {
      def builder = TemplateBuilder.instance
      assert builder.build("NOT A REAL TEMPLATE") == [
        contents:[
          [
            type:"text/plain; charset=utf-8",
            content:"CONTENT1",
          ],
          [
            type:"text/plain; charset=utf-8",
            content:"CONTENT2",
          ],
        ],
      ] 
      assert builder.exists("NOT A REAL TEMPLATE")
      assert builder.personalize("CONTENT", [:]) == "CONTENT"
    }
  }

  @Test
  void should_mock_itself_with_options_template_exists() {
    def mock = TemplateBuilder.mock(exists: false)

    mock.use {
      def builder = TemplateBuilder.instance
      assert !builder.exists("NOT A REAL TEMPLATE")
    }
  }

  @Test
  void should_mock_itself_with_options_custom_content() {
    def mock = TemplateBuilder.mock(content: "CUSTOM CONTENT")

    mock.use {
      def builder = TemplateBuilder.instance
      assert builder.build("NOT A REAL TEMPLATE") == [
        contents: [
          [
            type: "text/plain; charset=utf-8",
            content: "CUSTOM CONTENT",
          ]
        ]
      ]
    }
  }
  
  @Test
  void should_override() {
    def builder = TemplateBuilder.instance
    builder.cache.clear()
    def template = builder.load("template1")
    assert builder.personalize(template.contents[0].content,['user':'jsmith']) == "<html>hello jsmith</html>"
    assert template.fromEmail == "user@host.domain"
    builder.config.template.overrides = [:]
    builder.config.template.overrides.fromEmail = "jack@dandy.com"
    builder.config.template.overrides.user = "tom"
    builder.cache.clear()
    template = builder.load("template1")
    assert builder.personalize(template.contents[0].content,['user':'jsmith']) == "<html>hello tom</html>"
    assert template.fromEmail == "jack@dandy.com"
  }
}


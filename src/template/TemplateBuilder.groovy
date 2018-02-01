package template

import java.util.regex.*
import groovy.mock.interceptor.*
import log.Log

class TemplateBuilder {
  private final static Log log = Log.getLogger(this)

  static TemplateBuilder singleton

  static reset() {
    singleton = null
  }

  /*
   * Provide options like this:
   *   TemplateBuilder.mock(exists: false, content: "SOMETHING")
   */
  static mock(options = [:]) {
    def mock = new MockFor(TemplateBuilder)
    def builderProxyInstance = mock.proxyInstance()
    mock.ignore.getInstance() { new TemplateBuilder() }
    mock.ignore.build() { templateId -> 
      if (options.content == null) {
        [
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
      }
      else {
        [
          contents:[
            [
              type:"text/plain; charset=utf-8",
              content: options.content,
            ],
          ]
        ]        
      }
    }
    mock.ignore.exists() { options.exists != null ? options.exists : true }
    mock.ignore.personalize() { content, attributes, template = [:] -> content }

    mock
  }
  
  static TemplateBuilder getInstance() {
    if (!singleton) {
      synchronized(this) {
        if (!singleton) {
          singleton = new TemplateBuilder()
        }
      }
    }
    return singleton
  }  

  def config
  def cache = [:]
  
  TemplateBuilder() {
    config = [template:[:]]
    config.with {
      template.path = "${System.getenv("TOXIC_HOME")}/conf/templates"
      template.default = [:]
      template.overrides = [:]
    }
  }

  TemplateBuilder(Map configuration) {
    config = configuration
  }
  
  /*
   * @throws NotFound if the template does not exist
   */
  def load(templateId) {
    def template = cache[templateId]
    if (!template) {
      synchronized(this) {
        template = cache[templateId]
        if (!template) {
          template = loadTemplate(templateId)
          cache[templateId] = template
        }
      }
    }
    return template
  }

  /*
   * Determine if a template exists. Proves the directory exists in the configured template.path.
   */
  boolean exists(templateId) {
    try {
      def exists = cache[templateId]
      if (!exists) {
        load(templateId)
        exists = true
      }
      exists
    }
    catch (TemplateException e) {
      false
    }
  }
   
  def findTemplateDir(templateId) {
    return new File(config.template.path, templateId)
  }
  
  /*
   * @throws NotFound if the template does not exist
   */
  def loadTemplate(templateId) {
    // Use a simple map for now.  If there becomes a need for a more complex
    // template system this can be replaced with a formal class without 
    // requiring changes outside of this class, provided the map keys are
    // converted into similar class properties.
    def template = [contents:[]]
    def templateDir = findTemplateDir(templateId)
    if (!templateDir.isDirectory()) throw new TemplateException("Invalid template ID; templateDir=${templateDir.absolutePath}")
    def props = new Properties()
    templateDir.eachFile { file ->
      if (file.name == "template.info") {
        props.load(file.newInputStream())
        template.putAll(props)
        applyOverrides(template)
      }
      else {
        def entry = [:]
        entry.type = "${file.name.replaceAll("\\.", "/")}; charset=utf-8"
        entry.content = file.text
        template.contents << entry
        if (log.debug) log.debug("Loaded content for template; templateId=${templateId}, contentType=${entry.type}; contentLength=${entry.content.size()}")
      }
    }
    log.info("Loaded template; templateId=${templateId}, contentEntries=${template.contents.size()}; templateInfoPropCount=${props.size()}")
    return template
  }

  def personalize(content, attributes, template = [:]) {
    applyOverrides(attributes)
    attributes = attributes + template
    attributes.each { key, value -> 
      if (key != "contents") {
        if (!value) value = ""
        def pattern = "%%${key}%%".toString()
        content = content.replaceAll(pattern, Matcher.quoteReplacement(value))
      }
    }
    return content
  }
  
  /*
   * @throws NotFound if the template does not exist
   */
  def build(templateId) {
    return load(templateId)
  }
  
  def applyOverrides(map) {
    config?.template?.overrides.each { key, value ->
      if (map.containsKey(key)) {
        map[key] = value
      }
    }
  }
}


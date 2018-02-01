package template

import util.*

/*
 * def selector = TemplateSelector.select("email")
 * selector.credentialId = x
 * selector.templateType = y
 * selector.languageTag = en-US
 * println selector.templateId
 */
class TemplateSelector {
  
  static select(selectorType) {
    ClassFactory.factory("template.Selector", selectorType.capitalize())
  }
}


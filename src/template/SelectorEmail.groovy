package template

/*
 * Chooses a templateId to send emails to end users.
 */
class SelectorEmail {
  def owner
  def templateType
  def productCode
  def languageTag        // https://en.wikipedia.org/wiki/ISO_639-1 AND country code. en-US

  def getTemplateId() {
    def language
    if (languageTag) {
      language = Locale.forLanguageTag(languageTag).language
    }
    else {
      language = Locale.US.language
    }

    "${owner}_${templateType}_${productCode}_${language}"
  }
}

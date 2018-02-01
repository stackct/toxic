package util

class Resource {
  static asReader(resourceName) {
    asStream(resourceName).newReader()
  }

  static asStream(resourceName) {
    def classLoader = Thread.currentThread().contextClassLoader
    def resource = classLoader.getResourceAsStream(resourceName)

    if (!resource) throw new MissingResourceException("Resource not found: ${resourceName}", resourceName, "")

    resource
  }

  static path(resourceName) {
    def classLoader = Thread.currentThread().contextClassLoader
    def resource = classLoader.getResource(resourceName)
    
    if (!resource) throw new MissingResourceException("Resource not found: ${resourceName}", resourceName, "")

    resource.path
  }
}


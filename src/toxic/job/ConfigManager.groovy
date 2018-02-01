package toxic.job

import groovy.json.*

@Singleton
class ConfigManager {
  def write(JobManager mgr, String block, Map data) {
    if (!mgr) return
    write(mgr.configDir, block, data)
  }

  def write(String configDir, String block, Map data) {
    def dir = new File(configDir as String)
    if (!dir.isDirectory()) {
      dir.mkdirs()
    }
    def file = new File(dir, "${block.toLowerCase()}.json")
    file.text = JsonOutput.toJson(data) 
  }

  Map read(JobManager mgr, String block) {
    if (!mgr) return
    read(mgr.configDir, block)
  }

  Map read(String configDir, String block) {
    def map = [:]

    def file = new File(configDir as String, "${block.toLowerCase()}.json")
    if (file.isFile()) {
      def slurper = new JsonSlurper()
      map = slurper.parse(file)
    }
    return map
  }
}
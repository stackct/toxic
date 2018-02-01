package toxic.job

import toxic.ToxicProperties
import log.Log
import groovy.json.*

class EcrRepository implements DockerImageRepository {
  static Log log = Log.getLogger(EcrRepository.class)
 
  private String name
  private String profile
  private JsonSlurper slurper

  public EcrRepository(String name, ToxicProperties properties) {
    this.name = name
    this.slurper = new JsonSlurper()
    this.profile = properties['aws.ecr.profile'] ?: null
  }

  public Map getImages() {
    def images = [:]

    if (this.profile == null) {
      log.warn("AWS profile not set")
      return images
    }
    
    def result = exec("aws ecr list-images --repository ${name} --filter tagStatus=TAGGED", ['AWS_DEFAULT_PROFILE': this.profile])
    
    if (result.exitCode == 0) {

      slurper.parseText(result.out).with { obj ->
        obj.imageIds.each { image ->  
          images[image.imageDigest] = images[image.imageDigest] ?: []
          images[image.imageDigest] << image.imageTag
        }
      }
    }
    else {
      log.warn("Could not retrieve images; repository=${name}; exitCode=${result.exitCode}; stdout='${result.out}'; stderr='${result.err}'")
    }

    return images
  }

  private Map exec(String command, Map env=[:]) {
    int result = 1
    def stdout = new StringBuffer()
    def stderr = new StringBuffer()

    def envp = env.collect { k,v -> "${k}=${v}" } as String[]

    Runtime.runtime.exec(command, envp).with { proc ->
      proc.waitForProcessOutput(stdout, stderr)
      result = proc.exitValue()
    }

    return [out:stdout.toString(), err:stderr.toString(), exitCode:result]
  }
}

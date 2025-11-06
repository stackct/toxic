package toxic.dsl

import groovy.io.FileType
import log.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

class DepResolver {
  private final static Log log = Log.getLogger(this)
  public static final int ORAS_RETRY_LIMIT = 3

  String url
  File depsDir

  DepResolver(String depName, String depUrl, def props) {
    this.depsDir = new File(props.homePath, "gen/deps/${depName}")
    this.url = depUrl
  }

  void resolve() {
    File retrieveDir = depsDir.getParentFile()
    retrieveDir.mkdirs()

    this.orasPull(retrieveDir, this.url - 'oci://')

    retrieveDir.eachFile FileType.FILES, {
      try {
        extract(it)
      }
      finally {
        it.delete()
      }
    }
  }

  void orasPull(outputDir, url) {
    if(System.getenv('DOCKER_REGISTRY') && System.getenv('DOCKER_USER') && System.getenv('DOCKER_PASSWORD')) {
      "oras login ${System.getenv('DOCKER_REGISTRY')} -u ${System.getenv('DOCKER_USER')} -p ${System.getenv('DOCKER_PASSWORD')}"
              .execute()
              .waitForProcessOutput(System.out, System.err)
    }

    util.Wait.on { ->
      log.info("oras pull -o ${outputDir} ${url}")
      def sout = new StringBuilder(), serr = new StringBuilder()
      def proc = "oras pull -ko ${outputDir} ${url}".execute()
      proc.consumeProcessOutput(sout, serr)
      proc.waitForOrKill(10000)
      if(0 == proc.exitValue() ) {
        return true
      } else {
        throw new Exception("oras pull failed, try running an az acr login; url=${url}; exitCode=${proc.exitValue()}; sout=${sout}; serr=${serr}")
      }
    }.atMostAttempts(ORAS_RETRY_LIMIT).start()

  }

  private void extract(File depPackage) {
    depsDir.deleteDir()
    depsDir.mkdirs()

    switch (fileExtension(depPackage)) {
      case '.tgz':
        unTarGz(depsDir, depPackage)
        break
    }
  }

  private void unTarGz(File outputDir, File artifactFile) throws IOException {
    TarArchiveInputStream inputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(artifactFile))))
    TarArchiveEntry entry
    while ((entry = inputStream.getNextTarEntry()) != null) {
      if (entry.isDirectory()) {
        continue
      }
      File file = new File(outputDir, entry.getName())
      File parent = file.getParentFile()
      if (!parent.exists()) {
        parent.mkdirs()
      }
      IOUtils.copy(inputStream, new FileOutputStream(file))
    }
  }

  static String fileExtension(File file) {
    file.name.drop(file.name.lastIndexOf('.'))
  }
}

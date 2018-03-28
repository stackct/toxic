package toxic.dsl

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.io.FilenameUtils

class DepResolver {
  String url
  String username
  String password
  File depsDir

  DepResolver(String artifact, def props) {
    this.url = "${props.depsResolverBaseUrl}/${artifact}"
    this.username = props.depsResolverUsername
    this.password = props.depsResolverPassword
    this.depsDir = new File(props.homePath, "gen/deps/${artifact.take(artifact.lastIndexOf('.')) ?: artifact}")
  }

  void resolve() {
    extract(retrieve())
  }

  private File retrieve() {
    URL url = new URL(url)
    URLConnection urlConnection = url.openConnection()
    if(username && password) {
      String auth = "Basic " + "${username}:${password}".bytes.encodeBase64()
      urlConnection.setRequestProperty("Authorization", auth)
    }
    File retrieveDir = depsDir.getParentFile()
    retrieveDir.mkdirs()
    File artifactFile = new File(retrieveDir, FilenameUtils.getName(url.getPath()))
    OutputStream outputStream = artifactFile.newOutputStream()
    outputStream << urlConnection.inputStream
    outputStream.close()
    artifactFile
  }

  private void extract(File file) {
    depsDir.deleteDir()
    depsDir.mkdirs()
    switch (fileExtension(file)) {
      case '.tgz':
        unTarGz(depsDir, file)
        break
    }
    file.delete()
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

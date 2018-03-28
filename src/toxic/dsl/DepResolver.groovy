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
  String destDir
  String artifact

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
    new File(destDir).mkdirs()
    File artifactFile = new File(destDir, FilenameUtils.getName(url.getPath()))
    OutputStream outputStream = artifactFile.newOutputStream()
    outputStream << urlConnection.inputStream
    outputStream.close()
    artifactFile
  }

  private void extract(File file) {
    File outputDir = new File(destDir, artifact)
    outputDir.deleteDir()
    outputDir.mkdirs()
    switch (fileExtension(file)) {
      case '.tgz':
        unTarGz(outputDir, file)
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

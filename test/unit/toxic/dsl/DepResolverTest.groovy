package toxic.dsl

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.IOUtils
import org.junit.Test

import java.nio.file.Files

class DepResolverTest {
  @Test
  void should_construct() {
    def props = [homePath: '/tmp', 'pickle.ext': 'tgz']

    DepResolver depResolver = new DepResolver('foo', 'oci://localhost/pickle/foo:latest', props)
    assert 'oci://localhost/pickle/foo:latest' == depResolver.url
    assert '/tmp/gen/deps/foo' == depResolver.depsDir.absolutePath
  }

  @Test
  void should_resolve_tgz() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        depResolver.metaClass.orasPull = { outputDir, url ->
          Files.copy(file.toPath(), new File(outputDir, 'foo-pickle-latest.tgz').toPath())
        }
        depResolver.resolve()
        assert 2 == depResolver.depsDir.listFiles().size()

        File fnDir = new File(depResolver.depsDir, 'functions')
        assert 1 == fnDir.listFiles().size()
        assert 'function test{}' == new File(fnDir, 'test.fn').text

        File libDir = new File(depResolver.depsDir, 'library')
        assert 1 == libDir.listFiles().size()
        assert 'assert 1==1' == new File(libDir, 'test.groovy').text
      }
    }
  }

  @Test
  void should_remove_existing_dep_dir() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        depResolver.metaClass.orasPull = { outputDir, url ->
          Files.copy(file.toPath(), new File(outputDir, 'foo-pickle-latest.tgz').toPath())
        }
        new File(depResolver.depsDir, 'previousFnDir').mkdirs()
        depResolver.resolve()
        assert 2 == depResolver.depsDir.listFiles().size()
        assert new File(depResolver.depsDir, 'functions').exists()
        assert new File(depResolver.depsDir, 'library').exists()
      }
    }
  }

  @Test
  void should_resolve_latest_from_oci_format() {
    def actualOutputDir
    def actualUrl

    File tempDir
    try {
      tempDir = File.createTempDir()
      def props = ['pickle.ext': 'tgz', homePath: tempDir]
      DepResolver depResolver = new DepResolver('foo', 'oci://localhost/pickle/foo:latest', props)
      withTarGz { File file ->
        depResolver.metaClass.orasPull = { outputDir, url ->
          actualOutputDir = outputDir
          actualUrl = url
          Files.copy(file.toPath(), new File(outputDir, 'foo-pickle-latest.tgz').toPath())
        }
        depResolver.resolve()
      }
    }
    finally {
      tempDir?.deleteDir()
    }

    assert 'localhost/pickle/foo:latest' == actualUrl
  }

  void withResolver(Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      def props = [homePath: tempDir, 'pickle.ext': 'tgz']
      DepResolver depResolver = new DepResolver('foo', 'oci://localhost/pickle/foo:latest', props)
      c(depResolver)
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  void withTarGz(Closure c) {
    File file
    File tempDir
    try {
      file = File.createTempFile('dep_resolver', '.tgz')
      tempDir = File.createTempDir()
      File fnDir = new File(tempDir, 'functions')
      File libDir = new File(tempDir, 'library')

      fnDir.mkdirs()
      libDir.mkdirs()

      new File(fnDir, 'test.fn').text = 'function test{}'
      new File(libDir, 'test.groovy').text = 'assert 1==1'

      tarGz(file, fnDir, libDir)
      c(file)
    }
    finally {
      file?.delete()
      tempDir?.deleteDir()
    }
  }

  void tarGz(File file, File... dirs) {
    TarArchiveOutputStream outputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
    dirs.each {
      addFileToTarGz(outputStream, it)
    }
    outputStream.close()
  }

  void addFileToTarGz(TarArchiveOutputStream outputStream, File file, String base = '') {
    String entryName = "${base}${file.getName()}"
    TarArchiveEntry tarEntry = new TarArchiveEntry(file, entryName)
    outputStream.putArchiveEntry(tarEntry)

    if (file.isFile()) {
      IOUtils.copy(new FileInputStream(file), outputStream)
      outputStream.closeArchiveEntry()
    }
    else {
      outputStream.closeArchiveEntry()
      file.eachFile {
        addFileToTarGz(outputStream, it, "${entryName}/")
      }
    }
  }
}

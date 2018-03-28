package toxic.dsl

import groovy.mock.interceptor.MockFor
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.IOUtils
import org.junit.Test

class DepResolverTest {
  @Test
  void should_resolve_from_secure_repo() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        withFileUrl(file) { def requestProps ->
          depResolver.resolve()
          assert ['Authorization':'Basic Zm9vOmJhcg=='] == requestProps
        }
      }
    }
  }

  @Test
  void should_resolve_from_unsecure_repo() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        withFileUrl(file) { def requestProps ->
          depResolver.username = null
          depResolver.password = null
          depResolver.resolve()
          assert [:] == requestProps
        }
      }
    }
  }

  @Test
  void should_resolve_tgz() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        withFileUrl(file) { def requestProps ->
          depResolver.resolve()
          File outputDir = new File(depResolver.destDir, depResolver.artifact)
          assert 2 == outputDir.listFiles().size()

          File fnDir = new File(outputDir, 'functions')
          assert 1 == fnDir.listFiles().size()
          assert 'function test{}' == new File(fnDir, 'test.fn').text

          File libDir = new File(outputDir, 'library')
          assert 1 == libDir.listFiles().size()
          assert 'assert 1==1' == new File(libDir, 'test.groovy').text
        }
      }
    }
  }

  @Test
  void should_remove_existing_dep_dir() {
    withResolver { DepResolver depResolver ->
      withTarGz { File file ->
        withFileUrl(file) { def requestProps ->
          File outputDir = new File(depResolver.destDir, depResolver.artifact)
          new File(outputDir, 'previousFnDir').mkdirs()
          depResolver.resolve()
          assert 2 == outputDir.listFiles().size()
          assert new File(outputDir, 'functions').exists()
          assert new File(outputDir, 'library').exists()
        }
      }
    }
  }

  void withResolver(Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      DepResolver depResolver = new DepResolver(url: 'http://localhost'
          , username: 'foo'
          , password: 'bar'
          , destDir: tempDir
          , artifact: 'foobar'
      )
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

  void withFileUrl(File file, Closure c) {
    MockFor urlMock = new MockFor(URL)
    String url = "http://localhost/foo${DepResolver.fileExtension(file)}"
    def requestProps = [:]
    def mockUrlConnection = new URLConnection(new URL(url)){
      void connect() throws IOException { }
      InputStream getInputStream() throws IOException {
        file.newInputStream()
      }
      void setRequestProperty(String key, String value) {
        requestProps[key] = value
      }
    }
    urlMock.demand.openConnection(1) {
      mockUrlConnection
    }
    urlMock.demand.getPath(1) { url }
    urlMock.use {
      c(requestProps)
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

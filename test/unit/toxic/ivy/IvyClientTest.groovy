package toxic.ivy

import groovy.mock.interceptor.MockFor
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.retrieve.RetrieveReport
import org.apache.log4j.Level
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import javax.naming.spi.ResolveResult
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class IvyClientTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none()

  @Test
  void should_construct_with_defaults() {
    def systemMock = new MockFor(System)
    systemMock.demand.getenv(2) { "/\${${it}}" }

    IvyClient ivyClient
    systemMock.use {
      ivyClient = new IvyClient('foo', [homePath:'/tmp'])
    }
    assert 'dsl' == ivyClient.groupId
    assert 'foo' == ivyClient.artifactId
    assert 'latest.integration' == ivyClient.version
    assert '/tmp/gen/toxic/cache' == ivyClient.cacheDir.absolutePath
    assert '/tmp/gen/toxic/deps/foo' == ivyClient.depsDir.absolutePath
    assert 2 == ivyClient.ivyPropertyFiles.size()
    assert '/${HOME}/.ivy.properties' == ivyClient.ivyPropertyFiles[0].absolutePath
    assert '/${BUILD_COMMON_HOME}/ivydefault.properties' == ivyClient.ivyPropertyFiles[1].absolutePath
    assert '/${BUILD_COMMON_HOME}/ivysettings.xml' == ivyClient.ivySettingsFile.absolutePath
    assert 1 == ivyClient.artifactTypes.size()
    assert 'zip' == ivyClient.artifactTypes[0]
  }

  @Test
  void should_construct_with_build_common_path_override() {
    def systemMock = new MockFor(System)
    systemMock.demand.getenv(1) { "/\${${it}}" }

    IvyClient ivyClient
    systemMock.use {
      ivyClient = new IvyClient('foo', [homePath: '/tmp', buildCommonPath: '/buildcommonhome'])
    }
    assert 'dsl' == ivyClient.groupId
    assert 'foo' == ivyClient.artifactId
    assert 'latest.integration' == ivyClient.version
    assert '/tmp/gen/toxic/cache' == ivyClient.cacheDir.absolutePath
    assert '/tmp/gen/toxic/deps/foo' == ivyClient.depsDir.absolutePath
    assert 2 == ivyClient.ivyPropertyFiles.size()
    assert '/${HOME}/.ivy.properties' == ivyClient.ivyPropertyFiles[0].absolutePath
    assert '/buildcommonhome/ivydefault.properties' == ivyClient.ivyPropertyFiles[1].absolutePath
    assert '/buildcommonhome/ivysettings.xml' == ivyClient.ivySettingsFile.absolutePath
    assert 1 == ivyClient.artifactTypes.size()
    assert 'zip' == ivyClient.artifactTypes[0]
  }

  @Test
  void should_configure_ivy() {
    IvyClient ivyClient = new IvyClient('foo', [homePath:'/tmp'])
    def propFile
    def settingsFile
    try {
      propFile = File.createTempFile('ivy', '.properties')
      propFile.text = 'foo=bar'
      ivyClient.ivyPropertyFiles = [propFile]

      settingsFile = File.createTempFile('ivy', '.xml')
      settingsFile.text = '<ivysettings><caches defaultCacheDir="/tmp/ivycache" /></ivysettings>'
      ivyClient.ivySettingsFile = settingsFile

      Ivy ivy = ivyClient.ivyInstance()
      assert 'bar' == ivy.getVariable('foo')
      assert 'ivycache' == ivy.getSettings().getDefaultCache().name
    }
    finally {
      propFile?.delete()
      settingsFile?.delete()
    }
  }

  @Test
  void should_throw_exception_when_dependency_resolution_fails() {
    expectedException.expect(DependencyResolutionException.class)
    expectedException.expectMessage('Failed to resolve dependencies')

    IvyClient ivyClient = new IvyClient('foo', [homePath:'/tmp']) {
      @Override
      protected Ivy ivyInstance() {
        [resolve: { ModuleDescriptor md, ResolveOptions options ->
          new ResolveReport(md, null) {
            @Override
            public boolean hasError() { true }

            @Override
            List getAllProblemMessages() { ['Failed to resolve dependencies'] }
          }
        }] as Ivy
      }
    }

    ivyClient.resolve()
  }

  @Test
  void should_extract_resolved_zip_dependencies() {
    mockZipFile { File zipFile ->
      mockIvyClient([zipFile]) { IvyClient ivy ->
        ivy.resolve()
        assert 'key=value' == new File(ivy.depsDir, 'library/test.properties').text
      }
    }
  }

  @Test
  void should_log_when_no_files_are_found() {
    mockZipFile { File zipFile ->
      mockIvyClient([]) { IvyClient ivy ->
        IvyClient.log.track { logger ->
          ivy.resolve()
          assert !ivy.depsDir.isDirectory()
          assert logger.isLogged('No artifacts retrieved', Level.INFO)
        }
      }
    }
  }

  @Test
  void should_fail_when_multiple_artifacts_are_retrieved() {
    expectedException.expect(DependencyResolutionException.class)
    expectedException.expectMessage('Too many artifacts retrieved')

    mockIvyClient([new File('foo1.zip'), new File('foo2.zip')]) { IvyClient ivy ->
      ivy.resolve()
    }
  }

  def mockZipFile(Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      File tempLibDir = new File(tempDir, 'library')
      tempLibDir.mkdir()
      File tempFile = new File(tempLibDir, 'test.properties')
      tempFile.text = 'key=value'

      File zipFile = new File(tempDir, 'ivyClientTest.zip')
      ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))
      zipOutputStream.putNextEntry(new ZipEntry('library/'))
      zipOutputStream.putNextEntry(new ZipEntry("library/${tempFile.getName()}"))
      def buffer = new byte[1024]
      tempFile.withInputStream { inputStream ->
        int bytesRead = inputStream.read(buffer)
        if (bytesRead > 0) {
          zipOutputStream.write(buffer, 0, bytesRead)
        }
      }
      zipOutputStream.closeEntry()
      zipOutputStream.close()
      c(zipFile)
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  void mockIvyClient(List copiedFiles, Closure c) {
    File tempDir
    try {
      tempDir = File.createTempDir()
      IvyClient ivy = new IvyClient('foo', [homePath:tempDir.absolutePath]) {
        @Override
        protected Ivy ivyInstance() {
          [resolve: { ModuleDescriptor md, ResolveOptions options ->
            new ResolveReport(md, null) {
              @Override
              boolean hasError() { false }
            }
          },
           retrieve: { ModuleRevisionId mrid, RetrieveOptions options ->
             RetrieveReport retrieveReport = new RetrieveReport()
             retrieveReport.copiedFiles = copiedFiles
             retrieveReport
           }
          ] as Ivy
        }
      }
      c(ivy)
    }
    finally {
      tempDir?.deleteDir()
    }
  }
}

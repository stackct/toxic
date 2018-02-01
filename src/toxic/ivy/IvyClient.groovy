package toxic.ivy

import log.Log
import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.core.retrieve.RetrieveOptions
import org.apache.ivy.core.retrieve.RetrieveReport
import org.apache.ivy.util.filter.ArtifactTypeFilter

import java.util.zip.ZipFile

class IvyClient {
  private final static Log log = Log.getLogger(this)

  String groupId
  String artifactId
  String version
  File cacheDir
  File depsDir
  List ivyPropertyFiles = []
  File ivySettingsFile
  List artifactTypes = []

  IvyClient(String artifactId, def props) {
    File baseDir = new File(props.homePath, 'gen/toxic')
    String buildCommonPath = props.buildCommonPath ?: System.getenv('BUILD_COMMON_HOME')

    this.groupId = 'dsl'
    this.artifactId = artifactId
    this.version = 'latest.integration'
    this.cacheDir = new File(baseDir, 'cache')
    this.depsDir = new File(baseDir,"deps/${artifactId}")
    this.ivyPropertyFiles << new File("${System.getenv('HOME')}/.ivy.properties")
    this.ivyPropertyFiles << new File("${buildCommonPath}/ivydefault.properties")
    this.ivySettingsFile = new File("${buildCommonPath}/ivysettings.xml")
    this.artifactTypes << 'zip'
  }

  public void resolve() {
    Ivy ivy = ivyInstance()
    ResolveReport resolveReport = ivy.resolve(moduleDescriptor(), resolveOptions())
    if (resolveReport.hasError()) {
      throw new DependencyResolutionException(resolveReport.getAllProblemMessages().toString())
    }
    retrieve(ivy, resolveReport.getModuleDescriptor().getModuleRevisionId())
  }

  protected Ivy ivyInstance() {
    Ivy ivy = Ivy.newInstance()
    ivyPropertyFiles.each { loadProps(ivy, it) }
    ivy.configure(ivySettingsFile)
    ivy
  }

  private void loadProps(Ivy ivy, File propertiesFile) {
    Properties properties = new Properties()
    propertiesFile.withInputStream {
      properties.load(it)
    }
    properties.each { k, v ->
      ivy.setVariable(k, v)
    }
  }

  private ModuleDescriptor moduleDescriptor() {
    ModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(
      // give it some related name (so it can be cached)
      ModuleRevisionId.newInstance(groupId, artifactId+'-envelope', version)
    )
    ModuleRevisionId moduleRevisionId = ModuleRevisionId.newInstance(groupId, artifactId, version)
    DependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(moduleDescriptor, moduleRevisionId, false, false, false)
    dependencyDescriptor.addDependencyConfiguration('default', 'default')
    moduleDescriptor.addDependency(dependencyDescriptor)
    moduleDescriptor
  }

  private void retrieve(Ivy ivy, ModuleRevisionId moduleRevisionId) {
    RetrieveReport retrieveReport = ivy.retrieve(moduleRevisionId, retrieveOptions(cacheDir))
    List files = retrieveReport.copiedFiles.findAll {
      it.name.endsWith('.zip')
    }
    if(files.size() == 1) {
      depsDir.deleteDir()
      depsDir.mkdirs()
      extractZipFile(depsDir, files[0])
    }
    else if(files.size() == 0) {
      log.info('No artifacts retrieved')
    }
    else {
      throw new DependencyResolutionException('Too many artifacts retrieved')
    }
  }

  private ResolveOptions resolveOptions() {
    new ResolveOptions()
  }

  private RetrieveOptions retrieveOptions(File baseDir) {
    RetrieveOptions retrieveOptions = new RetrieveOptions()
    retrieveOptions.setDestArtifactPattern(baseDir.getAbsolutePath()+'/[artifact](-[classifier]).[ext]')
    retrieveOptions.setArtifactFilter(new ArtifactTypeFilter(artifactTypes))
    retrieveOptions.setConfs(['default'] as String[])
    retrieveOptions
  }

  protected void extractZipFile(File toDir, File file) {
    def zipFile = new ZipFile(file)
    zipFile.entries().each {
      if(it.isDirectory()) {
        new File(toDir, it.name).mkdirs()
      }
      else {
        new File(toDir, it.name).text = zipFile.getInputStream(it).text
      }
    }
  }
}

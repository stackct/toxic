package toxic.dsl

import log.Log
import toxic.dir.DirItem
import toxic.dir.LinkHandler

class DepHandler extends LinkHandler {
  private final static Log log = Log.getLogger(this)

  DepHandler(DirItem item, Object props) {
    super(item, props)
  }

  @Override
  File nextFile(File f) {
    if (!item.children) {
      lazyInit(f)
    }
    item.nextChild(props)
  }

  void lazyInit(File file) {
    props.deps = props.deps ?: [:]
    String prefix = 'deps.'
    props.findAll { k, v -> k.startsWith(prefix) && v }.each {
      props.deps[it.key.substring(prefix.size())] = it.value
    }
    Dep.parse(file.text).each {
      process(it)
    }
  }

  void process(Dep dep) {
    if(props.deps.containsKey(dep.name)) {
      processDepOverride(dep.name, props.deps[dep.name])
      return
    }

    if(!props['pickle.repoUrl']) {
      throw new DependencyResolutionException('Missing required property -pickle.repoUrl')
    }
    resolveRemote(dep.name, "${props['pickle.repoUrl']}/${dep.artifactId}:latest")
  }

  void processDepOverride(String depName, String depValue) {
    if(new File(depValue).isDirectory()) {
      resolveLocal(depName, depValue)
    }
    else {
      resolveRemote(depName, depValue)
    }
  }

  void resolveLocal(String depName, String depsDirPath) {
    props.deps[depName] = new File(depsDirPath)
    addFunctionsChild(depsDirPath)
  }

  void resolveRemote(String depName, String depUrl) {
    DepResolver depResolver = new DepResolver(depName, depUrl, props)
    if(!props.useDepsCache) {
      depResolver.resolve()
    }
    def depsDirPath = depResolver.depsDir.absolutePath
    props.deps[depName] = new File(depsDirPath)
    addFunctionsChild(depsDirPath)
  }

  void addFunctionsChild(String depsDir) {
    File fnDir = new File(depsDir, 'functions')
    if(fnDir.isDirectory() && fnDir.list().length > 0) {
      addChild(new DirItem(fnDir, item))
    }
    else {
      log.warn("skipping non-existent or empty deps function dir; fnDir=${fnDir.absolutePath}")
    }
  }
}

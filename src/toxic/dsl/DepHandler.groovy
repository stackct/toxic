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
      props.deps[it.key.substring(prefix.size())] = new File(it.value)
    }
    Dep.parse(file.text).each {
      process(it)
    }
  }

  void process(Dep dep) {
    if(props.deps.containsKey(dep.name)) {
      resolveLocal(props.deps[dep.name])
    }
    else {
      resolveRemote(dep)
    }
  }

  void resolveLocal(File depsDir) {
    addFunctionsChild(depsDir)
  }

  void resolveRemote(Dep dep) {
    DepResolver depResolver = new DepResolver(dep.artifactId, props)
    if(!props.useDepsCache) {
      depResolver.resolve()
    }
    props.deps[dep.name] = depResolver.depsDir
    addFunctionsChild(depResolver.depsDir)
  }

  void addFunctionsChild(def depsDir) {
    File fnDir = new File(depsDir, 'functions')
    if(fnDir.isDirectory() && fnDir.list().length > 0) {
      addChild(new DirItem(fnDir, item))
    }
    else {
      log.warn("skipping non-existent or empty deps function dir; fnDir=${fnDir.absolutePath}")
    }
  }
}

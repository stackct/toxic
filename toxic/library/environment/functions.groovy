import groovy.json.*

def helm = "helm"
def kubectl = "kubectl"
def env = System.getenv()

memory.parseEnvironment =  { String file ->
  new JsonSlurper().parseText(new File(file).text)  
}

memory.createNamespace = { ->
  return execWithEnv([kubectl, 'create', 'ns', memory['namespace']])
}

memory.deleteNamespace = { ->
  return execWithEnv([kubectl, 'delete', 'ns', memory['namespace']])
}

memory.kubePortForward = { ->
  def running = false
  if (memory.portForwardEnabled?.toString().equalsIgnoreCase("true")) {
    memory.lastStartedProc = null
    def exitCode = null
    // Spawn the port forward process in a separate thread, since this process will block.
    def thread = Thread.startDaemon("portfwd-${memory['name']}") {
      exitCode = execWithEnv([kubectl, '--namespace', memory['namespace'], 'port-forward', memory['name'], memory['port']])
    }

    // Wait for the process to start in the new thread (should take just a few milliseconds)
    while (!memory.lastStartedProc && exitCode == null) {
      sleep(100)
    }

    // Grab a copy of the Process object for use in the shutdown hook
    // Note that this could be clobbered by another thread if multiple threads are trying to 
    // exec multiple process concurrently. Since this function is expected to be performed
    // during the test setup phase, there is not an expectation of parallel activity during
    // port forward construction.
    def proc = memory.lastStartedProc

    // When the JVM exits, destroy the port forward process.
    thread.addShutdownHook() {
      proc?.destroy()
    }

    // If this port forward succeeded then the proc will not be null and the exitCode will still be null.
    running = proc != null && exitCode == null
  }
  return running
}

memory.helmInit = {
  return execWithEnv([helm, 'init', '--client-only'])
}

memory.helmRepoAdd = { String name, String url ->
  def cmds = []
  cmds << helm
  cmds << 'repo'
  cmds << 'add'
  memory.addHelmAuth(cmds)
  cmds << name
  cmds << url

  return execWithEnv(cmds)
}

memory.helmRepoUpdate = { ->
  return execWithEnv([helm, 'repo', 'update'])
}

memory.helmInstall = { String name, String chart, def values, def overrides = null ->
  int exitCode = memory.helmRepoUpdate()

  def namespace = memory['namespace']
  def release = namespace + '-' + name

  def cmds = []
  cmds << helm
  cmds << 'install'
  cmds << '--wait'
  cmds << '--namespace'; cmds << memory['namespace']
  cmds << '--name'; cmds << release
  memory.addHelmAuth(cmds)
  cmds << chart

  exitCode &= memory.execWithValues(cmds, values, overrides)
  exitCode &= memory.collectSummary(release, '-setup')
  exitCode &= memory.collectDetails(namespace, '-setup')

  return exitCode
}

memory.helmUpgrade = { String name, String chart, def values, def overrides ->
  int exitCode = 0
  
  def namespace = memory['namespace']
  def release = namespace + '-' + name

  def cmds = []
  cmds << helm
  cmds << 'upgrade'
  cmds << '--wait'
  cmds << release
  memory.addHelmAuth(cmds)
  cmds << chart

  exitCode &= memory.execWithValues(cmds, values, overrides)
  exitCode &= memory.collectSummary(release, '-upgrade')
  exitCode &= memory.collectDetails(namespace, '-upgrade')

  return exitCode
}

memory.execWithValues = { cmds, values, overrides -> 
  int exitCode = 0
  File f
  try {
    if (values) {
      f = File.createTempFile('values', '.json')
      f.write(new JsonBuilder(values).toString())
      cmds << '-f'; cmds << f.path
    }
    if (overrides) {
      cmds << '--set'; cmds << overrides
    }
    exitCode = execWithEnv(cmds)  
  }
  finally {
    f?.delete()
  }

  return exitCode
}

memory.collectSummary = { String release, String suffix = "", String outputDir = memory['artifactsDir'] ->
  execWithEnv([helm, 'status', release])
  new File(outputDir, "${release}-status${suffix}.log").write(out.toString())

  return 0
}

memory.collectDetails = { String namespace, String suffix = "", String outputDir = memory['artifactsDir'] ->
  execWithEnv([kubectl, '--namespace', namespace, 'describe', 'all'])
  new File(outputDir, "${namespace}-details${suffix}.log").write(out.toString())

  return 0
}

memory.helmDelete = { String name ->
  int exitCode = 0

  def namespace = memory['namespace']
  def release = namespace + '-' + name

  exitCode &= memory.collectSummary(release, '-teardown')
  exitCode &= memory.collectDetails(namespace, '-teardown')

  def cmds = []
  cmds << helm
  cmds << 'delete'
  cmds << '--purge'
  cmds << release

  exitCode = execWithEnv(cmds)

  return exitCode
}

memory.addHelmAuth = { List cmds -> 
  if (memory['secure.helm.username']) {
    cmds << '--username'; cmds << memory['secure.helm.username']
  }
  if (memory['secure.helm.password']) {
    cmds << '--password'; cmds << memory['secure.helm.password']
  }
}

memory.collectLogs = { String outputDir = memory['artifactsDir'], String namespace = memory['namespace'] ->
  memory.getPods(namespace).each { pod, containers ->
    containers.each { container ->
      def logs = memory.getLogs(pod, container, namespace)
      new File(outputDir, "${pod}-${container}.log").write(logs)
    }
  }

  return 0
}

memory.getPods = { String namespace = memory['namespace'] ->
  execWithEnv([kubectl, '-n', namespace, 'get', 'pods', '-o=json'])

  def response = out.toString()
  def parser = new JsonSlurper()
  def json = parser.parseText(response)

  Map pods = [:]
  json.items.findAll { it.kind == 'Pod' }.each { pod ->
    pods[pod.metadata.name] = pod.status.containerStatuses.collect { it.name }
  }

  return pods
}

memory.getLogs = { String pod, String container, String namespace = memory['namespace'] ->
  execWithEnv([kubectl, '-n', namespace, 'logs', pod, '-c', container])
  return out.toString()
}

memory.namespaceExists = { String namespace = memory['namespace'] ->
  execWithEnvNoLogging([kubectl, 'get', 'ns', '-o=json'])
  def ns = out.toString()
  
  new JsonSlurper().parseText(ns).items.any { item ->
    item.metadata.name == namespace
  }
}
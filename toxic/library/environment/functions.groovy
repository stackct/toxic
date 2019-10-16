import groovy.json.*

def helm = "helm"
def kubectl = "kubectl"
def env = System.getenv()
def execTimeout = new Integer(memory['execTimeout'] ?: 1200)

memory.parseEnvironment =  { String file ->
  new JsonSlurper().parseText(new File(file).text)
}

memory.createNamespace = { ->
  int exitCode = 0
  String out, err

  if (!memory.namespaceExists()) {
    exitCode = execWithEnv([kubectl, 'create', 'ns', memory['namespace']])
    out = out.toString()
    err = err.toString()
  }

  if (exitCode != 0) {
    log.warn("Namespace not created; reason='${err}'")
  }

  return 0
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

memory.kubeSecret = { String namespace, String name, String file ->
  execWithEnv([kubectl, '--namespace', namespace, 'create', 'secret', 'generic', name, '--from-file', file])
}

memory.kubeExportSecret = { String namespace, String name, String file ->
  int exitCode = execWithEnvNoLogging([kubectl, '--namespace', namespace, 'get', 'secret', name, '-o', 'yaml'])

  if (exitCode != 0) {
    log.warn("Secret not exported; reason='${err}'")
  } else {
    def str = out.toString()
    str = str.replaceAll(/creationTimestamp:.*/, "")
    str = str.replaceAll(/namespace:.*/, "")
    str = str.replaceAll(/resourceVersion:.*/, "")
    str = str.replaceAll(/selfLink:.*/, "")
    str = str.replaceAll(/uid:.*/, "")
    new File(file).text = str
  }

  return exitCode
}

memory.kubeApply = { String namespace, String file ->
  execWithEnv([kubectl, '--namespace', namespace, 'apply', '-f', file])
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
  cmds << '--timeout'; cmds << execTimeout.toString()
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
    exitCode = execWithEnv(cmds,[:],execTimeout)
  }
  finally {
    f?.delete()
  }

  return exitCode
}

memory.collectSummary = { String release, String suffix = "", String outputDir = memory['artifactsDir'] ->
  execWithEnv([helm, 'status', release])
  new File(outputDir, "${release}-status${suffix}.log").write(outputBuffer.toString())

  // Parse Load Balancer IP, if any - supports one LoadBalancer per chart
  outputBuffer.toString().eachLine { line ->
    if (line.contains("LoadBalancer")) {
      // Use space as a delimiter, but first make sure there are no extra spaces,
      // which can happen if an IP address contains fewer digits in some cases.
      10.times {
        line = line.replaceAll("  ", " ")
      }
      def pieces = line.split(" ")
      if (pieces.size() > 3) {
        memory.loadBalancerIp = pieces[3]
      }
    }
  }

  return 0
}

memory.collectDetails = { String namespace, String suffix = "", String outputDir = memory['artifactsDir'] ->
  execWithEnv([kubectl, '--namespace', namespace, 'describe', 'all'])
  new File(outputDir, "${namespace}-details${suffix}.log").write(outputBuffer.toString())

  return 0
}

memory.helmDelete = { String name ->
  def namespace = memory['namespace']
  def release = namespace + '-' + name

  memory.collectSummary(release, '-teardown')
  memory.collectDetails(namespace, '-teardown')

  def cmds = []
  cmds << helm
  cmds << 'delete'
  cmds << '--purge'
  cmds << release

  int exitCode = -1
  int attempts = 0
  int maxAttempts = 5
  while (exitCode != 0 && attempts++ < maxAttempts) {
    exitCode = execWithEnv(cmds)
  }

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

  def response = outputBuffer.toString()
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
  return outputBuffer.toString()
}

memory.namespaceExists = { String namespace = memory['namespace'] ->
  execWithEnvNoLogging([kubectl, 'get', 'ns', '--include-uninitialized=true', '-o=json'])
  def ns = outputBuffer.toString()

  new JsonSlurper().parseText(ns).items.any { item ->
    item.metadata.name == namespace
  }
}

memory.inParallelList = { List list, Closure c ->
  def threads = []

  list.each {
    threads << Thread.start { c(it) }
  }

  threads.each {
    it.join()
  }
}

memory.inParallelMap = { Map map, Closure c ->
  def threads = []

  map.each { key, value ->
    threads << Thread.start { c(key, value) }
  }

  threads.each {
    it.join()
  }
}

import toxic.ValidationException

if (memory.runtime == 'k8s') {
  execWithEnv(["kubectl", "-n", memory.namespace, "get", "pods", "--no-headers"])
  out.toString().eachLine { line ->
    if (line.startsWith(memory.containerName)) {
      def pieces = line.split(" ")
      if (pieces) {
        memory.podName = pieces[0]
      }
    }
  }
}

def k8sExec = ['kubectl', '-n', memory['namespace'], 'exec', memory['podName'], '-c', memory['containerName'], '--']
def k8sCopy = ['kubectl', 'cp', memory['src'], "${memory['namespace']}/${memory['podName']}:${memory['dest']}", '-c', memory['containerName']]

def dockerExec = ['docker', 'exec', memory['containerName']]
def dockerCopy = ['docker', 'cp', memory['src'], "${memory['containerName']}:memory['dest']"]

def touchFileCmd = ['/bin/sh', '-c', "touch ${memory['file']}"]
def fileExistsCmd = ['/bin/sh', '-c', "stat ${memory['file']}"]
def makeParentDirCmd = ['/bin/sh', '-c', "mkdir -p \$(dirname ${memory['dest']})"]
def standaloneCopy = ['/bin/sh', '-c', "cp ${memory['src']} ${memory['dest']}"]

def fileCmds = [
    'k8s': [
        'create': k8sExec + touchFileCmd,
        'exists': k8sExec + fileExistsCmd,
        'mkParentDir': k8sExec + makeParentDirCmd,
        'copy': k8sCopy,
    ],
    'docker': [
        'create': dockerExec + touchFileCmd,
        'exists': dockerExec + fileExistsCmd,
        'mkParentDir': dockerExec + makeParentDirCmd,
        'copy': dockerCopy,
    ],
    'standalone': [
        'create': touchFileCmd,
        'exists': fileExistsCmd,
        'mkParentDir': makeParentDirCmd,
        'copy': standaloneCopy,
    ]
]

if(!fileCmds.containsKey(memory.runtime)) {
  throw new ValidationException("Unsupported runtime; runtime=${memory.runtime}")
}

memory['file.cmds'] = fileCmds
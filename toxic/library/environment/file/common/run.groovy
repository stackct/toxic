import toxic.ValidationException

def k8sExec = ['kubectl', '-n', memory['namespace'], 'exec', memory['podName'], '-c', memory['containerName'], '--']
def dockerExec = ['docker', 'exec', memory['containerName']]

def touchFileCmd = ['/bin/sh', '-c', "touch ${memory['file']}"]
def fileExistsCmd = ['/bin/sh', '-c', "stat ${memory['file']}"]

def fileCmds = [
    'k8s': [
        'create': k8sExec + touchFileCmd,
        'exists': k8sExec + fileExistsCmd,
    ],
    'docker': [
        'create': dockerExec + touchFileCmd,
        'exists': dockerExec + fileExistsCmd,
    ],
    'standalone': [
        'create': touchFileCmd,
        'exists': fileExistsCmd
    ]
]

if(!fileCmds.containsKey(memory.runtime)) {
  throw new ValidationException("Unsupported runtime; runtime=${memory.runtime}")
}

memory['file.cmds'] = fileCmds
execWithEnv(['/bin/sh', '-c', "kubectl -n ${memory['namespace']} get pods | grep '^${memory['prefix']}' | awk '{print \$1}'"])
memory.name = out.toString().trim()
memory['hostname'] = "${memory['service']}.${memory['namespace']}.svc.cluster.local"
memory['url'] = "http://${memory.hostname}"

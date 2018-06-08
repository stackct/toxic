import util.Wait

// Collect the logs:
memory.collectLogs()

// Delete the charts
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  memory.helmDelete(chart)
}

// Delete the namespace
memory.deleteNamespace()

// Wait for namespace to be deleted
int wait = 1000 * 300
Wait.on { -> !memory.namespaceExists() }.every(3000).atMostMs(wait).start()

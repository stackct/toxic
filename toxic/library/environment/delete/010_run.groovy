import util.Wait

// Delete the charts
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  memory.helmDelete(chart)
}

// Delete the namespace
memory.deleteNamespace()


// Wait for namespace to be deleted
int wait = 1000 * 600
int interval = 1000 * 3

Wait.on { -> !memory.namespaceExists() }.every(interval).atMostMs(wait).start()

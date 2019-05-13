import util.Wait

// Delete the charts
memory.inParallelMap(memory.parseEnvironment(memory.spec).charts, { chart, props ->
  memory.helmDelete(chart)
})

// Delete the namespace
memory.deleteNamespace()


// Wait for namespace to be deleted
int wait = 1000 * 600
int interval = 1000 * 3

Wait.on { -> !memory.namespaceExists() }.every(interval).atMostMs(wait).start()

// This is ugly but since Kubernetes lies about the namespace being deleted when it's 
// still being terminated we have no choice to but wait 5 seconds and hope it's actuall
// completely gone.
sleep(5000)
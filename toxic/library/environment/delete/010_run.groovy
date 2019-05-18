import util.Wait

// Delete the charts
memory.inParallelMap(memory.parseEnvironment(memory.spec).charts) { chart, props ->
  memory.helmDelete(chart)
}

// Delete the namespace
memory.deleteNamespace()


// Wait for namespace to be deleted
int wait = 1000 * 600
int interval = 1000 * 3
int verifications = 3

// Occassionally kubectl returns a failure when querying namespaces. Check multiple
// times for the namespace before concluding it truly does not exist.
verifications.times {
  Wait.on { -> !memory.namespaceExists() }.every(interval).atMostMs(wait).start()
  sleep(interval)
}

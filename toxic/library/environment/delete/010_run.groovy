// Collect the logs:
assert 0 == memory.collectLogs()

// Delete the charts
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  assert 0 == memory.helmDelete(chart)
}

// Delete the namespace
assert 0 == memory.deleteNamespace()

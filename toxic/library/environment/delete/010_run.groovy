// Collect the logs:
memory.collectLogs()

// Delete the charts
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  memory.helmDelete(chart)
}

// Delete the namespace
memory.deleteNamespace()

// Give it time to delete
// TODO - Improve by checking when deleted, or see if kubectl can only return after completed
sleep 10000
// Create the namespace
assert 0 == memory.createNamespace()

// Configure repositories
memory.parseEnvironment(memory.spec).repositories.each { name, url ->
  assert 0 == memory.helmRepoAdd(name,url)
}

// Install all the charts
memory.inParallelMap(memory.parseEnvironment(memory.spec).charts) { chart, props ->
  assert 0 == memory.helmInstall(chart, props.chart, props.values)
}

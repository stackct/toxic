// Create the namespace
assert 0 == memory.createNamespace()

// Init helm
assert 0 == memory.helmInit()

// Configure repositories
memory.parseEnvironment(memory.spec).repositories.each { name, url ->
  assert 0 == memory.helmRepoAdd(name,url)
}

// Install all the charts
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  assert 0 == memory.helmInstall(chart, props.chart, props.values)
}

// Sleep while ingress rules get applied to nginx
sleep 10000
memory.parseEnvironment(memory.spec).charts.each { chart, props ->
  if (chart == memory.chart) {
    assert 0 == memory.helmUpgrade(chart, props.chart, props.values, memory.overrides)
  }
}

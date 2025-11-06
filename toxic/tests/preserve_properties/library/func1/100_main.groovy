// Set optional argument that was not provided in Step
if (memory.isNothing('bar')) {
  memory["bar"] = "generated-by-func1"
}

// Set property that has nothing to do with this function
memory["delete"] = "1"

memory["closure"] = {
  assert 1 == 1
}
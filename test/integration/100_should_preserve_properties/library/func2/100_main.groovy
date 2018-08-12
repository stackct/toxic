// Set optional argument that was not provided in Step
if (memory.isNothing('bar')) {
  memory["bar"] = "generated-by-func2"
}

// Set property that has nothing to do with this function
memory["delete"] = "2"
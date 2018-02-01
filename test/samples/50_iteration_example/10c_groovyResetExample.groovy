// Reset iterations, but first force iteration loop to stop so that
// this script only executes once.
memory.taskIteration=memory.taskIterations
memory.taskIterations = memory.myOldTaskIterations

// Reset memory back to prior state before this test
memory.remove("myCurrentValue")
memory.remove("myInputValues")
memory.remove("myOldTaskIterations")
memory.xmlMethod = memory.myOldMethod
memory.remove("myOldMethod")

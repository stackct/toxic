// Save the original memory variables
memory.myOldTaskIterations = memory.taskIterations

// Setup the next test to repeat 5 times, using a different
// input value each time
memory.taskIterations = 5
memory.myInputValues = ["abc", "def", "ghi", "jkl", "mno"]
memory.myCurrentValue = "`memory.myInputValues[memory.taskIteration]`"

// Save the original method variable and setup a custom value for this test
memory.myOldMethod = memory.xmlMethod
memory.xmlMethod = "GET /title.html?hostname=%myCurrentValue% HTTP/1.1"

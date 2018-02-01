// Perform any additional validations
assert memory.myStatus == "200"
assert memory.myAdvancedContentLength == memory.myContentLength

// Reset memory back to prior state before this chained test
memory.remove("myStatus")
memory.remove("myHostname")
memory.xmlMethod = memory.myOldMethod
memory.remove("myOldMethod")
memory.remove("myAdvancedContentLength")

// Illustrates how a groovy task could run shell scripts
def cmd = "hostname"
def proc = cmd.execute()

// If you do not need the output of the process, start a thread to read output and throw it away,
// otherwise the output buffer may fill and the process will hang
//proc.consumeProcessOutput()
proc.waitForOrKill(5* 60 * 1000) // Don't use waitFor(), always specify a timeout value

// Memory is a hashmap that is available to all Toxic tasks and is
// pre-bound to this script.
memory.myHostname = proc.text?.trim()

// Perform any validations and throw exceptions if the task has failed
assert memory.myHostname != null

// Save the original method variable and setup a custom value for this test
memory.myOldMethod = memory.xmlMethod
memory.xmlMethod = "GET /title.html?hostname=%myHostname% HTTP/1.1"

// Verify that the content length is 76 when analyzing the response
memory.myContentLength="76"

// Illustrate how to embed padding into variable substitutions
memory.myTitle="Sample"
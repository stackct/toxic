import java.nio.file.*

def cmds = []
cmds << 'compare'
cmds << '-alpha'
cmds << 'Remove'
cmds << '-compose'
cmds << 'Src'
cmds << '-metric'
cmds << 'AE'
cmds << "${memory.imageFile}"
cmds << "${memory.reconstructFile}"
cmds << "${memory.differenceFile}"

memory.expectedMatchesBaseline = ('0' == execWithEnv(cmds).toString().trim())

if(memory.expectedMatchesBaseline || !memory.rebasePrompt) {
  return
}

if (java.awt.Desktop.isDesktopSupported()) {
    try { java.awt.Desktop.getDesktop().open(new java.io.File("${memory.imageFile}")) } catch (IOException ex) { }
    try { java.awt.Desktop.getDesktop().open(new java.io.File("${memory.reconstructFile}")) } catch (IOException ex) { }
    try { java.awt.Desktop.getDesktop().open(new java.io.File("${memory.differenceFile}")) } catch (IOException ex) { }
}

def promptResponse = System.console().readLine "" +
    "\n---Detected image diff---" +
    "\nimageFile=${memory.imageFile}" +
    "\nreconstructFile=${memory.reconstructFile}" +
    "\ndifferenceFile=${memory.differenceFile}" +
    "\nbaselineFile=${memory.baselineFile}" +
    "\n" +
    "\nUpdate baseline? y/n" +
    "\n"
if('y' != promptResponse) {
  log.info("Baseline not updated")
  return
}

memory.expectedMatchesBaseline = true
Files.copy(Paths.get(memory.reconstructFile), Paths.get(memory.baselineFile), StandardCopyOption.REPLACE_EXISTING)
log.info("Baseline updated")
import util.Wait

def cmd = memory['file.cmds'][memory.runtime]
println("TWRR >> cmd ${cmd}")

if(memory.create) {
  assert 0 == execWithEnv(cmd.create)
}

Wait.on { -> 0 != execWithEnv(cmd.exists) }.every(memory.intervalMs).atMostMs(memory.maxWaitMs).start()
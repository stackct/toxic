import util.Wait

Wait.on { -> 
    memory.success = memory.requestCounter >= memory.waitForCount
    return memory.success
}.every(1000).atMostMs(memory.waitForAtMostMilliseconds).start()
import util.Wait

Wait.on { -> 
    ok = memory.requestCounter >= memory.waitForCount
    return ok
}.every(1000).atMostMs(memory.waitForAtMostMilliseconds).start()

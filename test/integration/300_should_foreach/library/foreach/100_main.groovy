def count = memory.step.item.count ? memory.step.item.count.toInteger() : 0
def sum = memory.step.item.sum ? memory.step.item.sum.toInteger() : 0

memory.count = count + 1
memory.sum = sum + memory.item.toInteger()
memory.value = memory.item
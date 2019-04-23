def oldTimeZone = TimeZone.getDefault()

if (memory['utc']) {
  TimeZone.setDefault(TimeZone.getTimeZone('UTC'))
}

use (groovy.time.TimeCategory) {
  memory.date = (new Date() + memory['offsetDays'].toInteger().days).format(memory['format'])
}

TimeZone.setDefault(oldTimeZone)
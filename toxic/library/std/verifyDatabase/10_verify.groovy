import groovy.sql.Sql
import util.Wait

memory.waitMs = memory.waitMs ?: 5000

Wait.on { -> 
  def databaseState
  try {
    def connection = Sql.newInstance(memory.sqlUrl, memory.sqlUser, memory.sqlPass, memory.sqlDriver)
    databaseState = 1 == connection.rows("select 1").size()
  }
  catch (java.sql.SQLException e) {
    databaseState = false
  }
  return databaseState
}.every(1000).atMostMs(memory.waitMs).start()


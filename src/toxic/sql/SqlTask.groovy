
package toxic.sql

import toxic.CompareTask
import toxic.ToxicProperties
import org.apache.log4j.Logger
import groovy.sql.Sql

import java.sql.Clob
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types

public class SqlTask extends CompareTask {
  protected static Logger slog = Logger.getLogger(SqlTask.class.name)

  @Override
  def prepare(Object request) {
    verifyProperties()
    super.prepare(request)
  }

  // Verify required properties are set
  private verifyProperties() {
    def requiredProps = ["sqlUrl", "sqlUser", "sqlPass", "sqlDriver"]
    def missingProps = requiredProps.findAll { !props.containsKey(it) }
    if(missingProps) {
      throw new IllegalArgumentException("Missing required properties: ${missingProps.join(",")}")
    }
  }

  protected String toCsv(List<Map> rows) {
    def csvRows = rows.collect { row ->
      if(slog.isTraceEnabled()) {
        log.trace("Column values: ")
        row.each { cname, cval ->
          def colClass = null
          if(cval != null) { colClass = cval.getClass() }
          def buf = "***** ${cname.padRight(25)} ${colClass}: ${cval}"
          if(Timestamp.class == colClass) {
            buf += "(nanos: ${cval.nanos})"
          }
          slog.trace(buf)
         }
      }
      row.values().collect { v -> toStringVal(v) }.join(",")
    }
    return csvRows.join("\n")
  }

  private String toStringVal(Object obj) {
    def val = obj
    
    if (obj instanceof java.math.BigDecimal) {
      val = obj.toPlainString()
    }
    else if(obj instanceof Clob) {
      StringBuilder sb = new StringBuilder()
      BufferedReader br = new BufferedReader(obj.getCharacterStream())
      String line
      while(null != (line = br.readLine())) {
        sb.append(line)
      }
      br.close()
      val = sb.toString()
    }

    return val.toString()
  }

  protected def query(String sql) {
    if (props.sqlVerbose == "true") {
      log.info("Submitting query; url=" + props.sqlUrl + ":" + props.sqlUser + ":" + props.sqlPass + "; Driver=" + props.sqlDriver + ":\n" + sql)
    }
    def dumpMeta = { meta ->
      if(slog.isTraceEnabled()) {
        def buf = ""
        (1..meta.columnCount).each {
          buf += "   " + meta.getColumnLabel(it).padRight(20) + " " + meta.getColumnTypeName(it).padRight(20)
          buf += "\n"
        }
        slog.trace("Result column types:\n" + buf)
      }
    }
    return props.sqlConnection.rows(sql, dumpMeta)
  }

  protected def execute(String sql) {
    if (props.sqlVerbose == "true") {
      log.info("Executing statement(s); url=" + props.sqlUrl + ":" + props.sqlUser + ":" + props.sqlPass + "; Driver=" + props.sqlDriver + ":\n" + sql)
    }

    def results = []
    props.sqlConnection.execute(sql) { boolean isResultSet, def rowResultOrAffectedCount ->
      if(isResultSet) {
        results << rowResultOrAffectedCount
      }
      else {
        results << "${rowResultOrAffectedCount} row(s) affected"
      }
    }
    results.join('\n')
  }

  protected transmit(request, expectedResponse, def memory) {
    synchronized (props) {
      if (!props.sqlConnection || memory.sqlReconnect == "true") {
        props.sqlConnection = Sql.newInstance(props.sqlUrl, props.sqlUser, props.sqlPass, props.sqlDriver)
      }
    }

    int sqlRetries = 0
    try {
      sqlRetries = new Integer(memory.sqlRetries)
    } catch (Exception e) {
      log.debug("sqlRetries properties is not set, defaulting to 0 retries; error=${e.message}")
    }
    memory.lastRequest = "Sending to host=" + props.sqlUrl + "; retries=${sqlRetries}:${request}"

    def result
    int attempts = 0
    while (attempts++ <= sqlRetries) {
      try {
        if (request.toLowerCase().startsWith("select")) {
          result = toCsv(query(request))
        } else {
          result = execute(request)
        }
        break
      } catch (SQLException se) {
        if (!se.toString().toLowerCase().contains("timeout")) throw se
        if (attempts > sqlRetries) {
          log.error("Exceeded allowed connection attempts; attempts=${attempts}", se)
          throw se
        } else {
          log.warn("Sql timeout exception; attempts=${attempts}", se)
        }
      }
    }

    if (memory.sqlVerbose == "true") {
      log.info("Received:\n" + result)
    }

    return result.toString()
  }
}

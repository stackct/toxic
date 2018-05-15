
package toxic.sql

import groovy.mock.interceptor.MockFor
import groovy.sql.Sql
import org.junit.*
import toxic.ToxicProperties

public class SqlTaskTest {
  @Test
  public void testInitSqlFile() {
    def st = new SqlTask()
    File f = new File("initSqlTest_req.sql")
    f.createNewFile()
    st.init(f, null)
    f.delete()
  }

  @Test
  public void testInitUnconformingSqlFile() {
    def st = new SqlTask()
    File f = new File("initUnconformingSqlTest.sql")
    f.createNewFile()
    try {
      st.init(f, null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
    f.delete()
  }

  @Test
  public void testInitDir() {
    def st = new SqlTask()
    try {
      st.init(new File("test"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitMissingSqlFile() {
    def st = new SqlTask()
    try {
      st.init(new File("sdfsdf324"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitNotSqlFile() {
    def st = new SqlTask()
    try {
      st.init(new File("VERSION"), null)
      assert false : "Expected IllegalArgumentException"
    } catch (IllegalArgumentException iae) {
    }
  }

  @Test
  public void testInitString() {
    def st = new SqlTask()
    st.init("sdf", null)
  }

  @Test
  public void testLookupExpectedResponse() {
    def st = new SqlTask()
    File respf = new File("lookup_resp.sql")
    respf.text="test"
    def result = st.lookupExpectedResponse(new File("lookup_req.sql"))
    respf.delete()
    assert result == "test"
  }

  @Test
  public void testLookupExpectedResponseMissing() {
    def st = new SqlTask()
    assert st.lookupExpectedResponse(new File("missing_req.sql")) == null
  }

  @Test
  public void testConversionToCsv() {
    def rows = [[:]]

    rows[0]["string"]     = "foo" as String
    rows[0]["integer"]    = 99 as Integer
    rows[0]["bigdecimal"] = "0.000000000" as java.math.BigDecimal

    assert new SqlTask().toCsv(rows) == 'foo,99,0.000000000'
  }

/*
  @Test
  public void testTransmit() {
    ToxicProperties tp = new ToxicProperties()
    tp.sqlUrl="jdbc:jtds:sqlserver://lab-sql-1/master"
    tp.sqlUser="sa"
    tp.sqlPass="Test12"
    tp.sqlDriver="net.sourceforge.jtds.jdbc.Driver"

    SqlTask st = new SqlTask()
    st.init(null, tp)
    def result = st.transmit("SELECT 123 as A, 456 as B, 789 as C", tp)
    assert result == "123,456,789"
  }
*/  
  @Test
  public void testConnection() {
    ToxicProperties tp = new ToxicProperties()
    tp.sqlUrl="jdbc:jtds:sqlserver://lab-sql-1/master"
    tp.sqlUser="sa"
    tp.sqlPass="Test12"
    tp.sqlDriver="net.sourceforge.jtds.jdbc.Driver"
    
    def connMock = new Object() {
      def rows = { sql, rsMeta -> [[c1:"123",c2:"456",c3:"789"]] }
      def execute = { sql ->  }
    }
    
    def sqlMock = new MockFor(Sql)
    sqlMock.demand.newInstance { s1, s2, s3, s4 -> connMock }

    SqlTask st = new SqlTask()
    st.init(null, tp)
    
    def result 
    sqlMock.use {
      result = st.transmit("SELECT 123 as A, 456 as B, 789 as C", tp)
    }
    assert result == "123,456,789"
  }

  @Test
  public void testChecksRequiredProperties() {
    def propsWithout = { propToRemove ->
      ToxicProperties tp = new ToxicProperties()
      tp.sqlUrl="url"
      tp.sqlUser="user"
      tp.sqlPass="pwd"
      tp.sqlDriver="driver"
      if(propToRemove) {
        tp.remove(propToRemove)
      }
      return tp
    }

    SqlTask task = new SqlTask()
    task.init("test",propsWithout(null))
    assert "test" == task.prepare("test")

    def check = { props ->
      try {
        SqlTask st = new SqlTask()
        st.init("test", props)
        assert "test" == st.prepare("test")
        Assert.fail("Did not throw expected exception")
      } catch(IllegalArgumentException e) {
        // expected
      }
    }

    check(propsWithout("sqlUrl"))
    check(propsWithout("sqlUser"))
    check(propsWithout("sqlPass"))
    check(propsWithout("sqlDriver"))

    def msg
    try {
      SqlTask st = new SqlTask()
      st.init("test", new ToxicProperties())
      st.prepare("test")
    } catch(IllegalArgumentException ex) {
      msg = ex.getMessage()
    }
    assert msg.contains("sqlUrl,sqlUser,sqlPass,sqlDriver")
  }

  @Test
  void should_execute_with_output() {
    assert '' == mockSqlExecute([])
    assert '1 row(s) affected' == mockSqlExecute([[false, 1]])
    assert '1 row(s) affected\n2 row(s) affected' == mockSqlExecute([[false, 1], [false, 2]])
    assert '[[id:12345]]' == mockSqlExecute([[true, [[id:12345]]]])
    assert '[[id:1]]\n[[id:2]]' == mockSqlExecute([[true, [[id:1]]], [true, [[id:2]]]])
    assert '1 row(s) affected\n[[id:2]]' == mockSqlExecute([[false, 1], [true, [[id:2]]]])
  }

  def mockSqlExecute(def results) {
    ToxicProperties toxicProperties = new ToxicProperties()
    toxicProperties.sqlConnection = [execute: { String sql, Closure processResults ->
      results.each {
        processResults(it[0], it[1])
      }
    }]

    SqlTask task = new SqlTask()
    task.init("test", toxicProperties)
    task.execute('EXEC stored_procedure')
  }
}
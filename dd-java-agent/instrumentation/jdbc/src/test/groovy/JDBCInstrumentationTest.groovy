import datadog.trace.agent.test.AgentTestRunner
import org.apache.derby.jdbc.EmbeddedDriver
import org.h2.Driver
import spock.lang.Shared
import spock.lang.Unroll

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class JDBCInstrumentationTest extends AgentTestRunner {
  @Shared
  def dbName = "jdbcUnitTest"

  @Unroll
  def "Connection constructor throwing then generating correct spans after recovery using #db connection (prepare statement = #prepareStatement)"() {
    setup:
    Connection connection = null

    when:
    try {
      connection = new DummyThrowingConnection()
    } catch (Exception e) {
      connection = driver.connect(url, null)
    }

    Statement statement = null
    ResultSet rs = null
    if (prepareStatement) {
      statement = connection.prepareStatement(query)
      rs = statement.executeQuery()
    } else {
      statement = connection.createStatement()
      rs = statement.executeQuery(query)
    }

    then:
    rs.next()
    rs.getInt(1) == 3
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1

    and:
    def span = trace[0]
    span.context().operationName == "${db}.query"
    span.serviceName == db
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0

    def tags = span.context().tags
    tags["db.type"] == db
    tags["db.user"] == user
    tags["span.kind"] == "client"
    if (prepareStatement) {
      tags["component"] == "java-jdbc-prepared_statement"
    } else {
      tags["component"] == "java-jdbc-statement"
    }

    tags["db.jdbc.url"].contains(db)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == user == null ? 7 : 8

    cleanup:
    if (statement != null) {
      statement.close()
    }
    if (connection != null) {
      connection.close()
    }

    where:
    prepareStatement | db       | driver               | url                                            | user  | query
    true             | "h2"     | new Driver()         | "jdbc:h2:mem:" + dbName                        | null  | "SELECT 3;"
    true             | "derby"  | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP" | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    false            | "h2"     | new Driver()         | "jdbc:h2:mem:" + dbName                        | null  | "SELECT 3;"
    false            | "derby"  | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP" | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

}

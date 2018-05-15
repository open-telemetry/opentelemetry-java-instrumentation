import datadog.trace.agent.test.AgentTestRunner
import org.apache.derby.jdbc.EmbeddedDriver
import org.h2.Driver
import org.hsqldb.jdbc.JDBCDriver
import spock.lang.Shared
import spock.lang.Unroll

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class JDBCInstrumentationTest extends AgentTestRunner {
  @Shared
  def dbName = "jdbcUnitTest"

  @Shared
  private Map<String, Connection> connections

  def setupSpec() {
    Connection h2Connection = new Driver().connect("jdbc:h2:mem:" + dbName, null)
    Connection hsqlConnection = new JDBCDriver().connect("jdbc:hsqldb:mem:" + dbName, null)
    Connection derbyConnection = new EmbeddedDriver().connect("jdbc:derby:memory:" + dbName + ";create=true", null)

    connections = [
      h2    : h2Connection,
      derby : derbyConnection,
      hsqldb: hsqlConnection,
    ]
  }

  def cleanupSpec() {
    connections.values().each {
      it.close()
    }
  }

  @Unroll
  def "basic statement on #driver generates spans"() {
    setup:
    Statement statement = connection.createStatement()
    ResultSet resultSet = statement.executeQuery(query)

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == username
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == username == null ? 7 : 8

    cleanup:
    statement.close()

    where:
    driver   | connection                | username | query
    "h2"     | connections.get("h2")     | null     | "SELECT 3"
    "derby"  | connections.get("derby")  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "prepared statement execute on #driver generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    assert statement.execute()
    ResultSet resultSet = statement.resultSet

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == username
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == username == null ? 7 : 8

    cleanup:
    statement.close()

    where:
    driver   | connection                | username | query
    "h2"     | connections.get("h2")     | null     | "SELECT 3"
    "derby"  | connections.get("derby")  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "prepared statement query on #driver generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = statement.executeQuery()

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == username
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == username == null ? 7 : 8

    cleanup:
    statement.close()

    where:
    driver   | connection                | username | query
    "h2"     | connections.get("h2")     | null     | "SELECT 3"
    "derby"  | connections.get("derby")  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "statement update on #driver generates a span"() {
    setup:
    Statement statement = connection.createStatement()
    def sql = connection.nativeSQL(query)

    expect:
    !statement.execute(sql)
    statement.updateCount == 0

    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == username
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == username == null ? 7 : 8

    cleanup:
    statement.close()

    where:
    driver   | connection                | username | query
    "h2"     | connections.get("h2")     | null     | "CREATE TABLE S_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | connections.get("derby")  | "APP"    | "CREATE TABLE S_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | connections.get("hsqldb") | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }

  @Unroll
  def "prepared statement update on #driver generates a span"() {
    setup:
    def sql = connection.nativeSQL(query)
    PreparedStatement statement = connection.prepareStatement(sql)

    expect:
    statement.executeUpdate() == 0
    TEST_WRITER.size() == 1

    def trace = TEST_WRITER.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == username
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == username == null ? 7 : 8

    cleanup:
    statement.close()

    where:
    driver   | connection                | username | query
    "h2"     | connections.get("h2")     | null     | "CREATE TABLE PS_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | connections.get("derby")  | "APP"    | "CREATE TABLE PS_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | connections.get("hsqldb") | "SA"     | "CREATE TABLE PUBLIC.PS_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }
  
  @Unroll
  def "connection constructor throwing then generating correct spans after recovery using #driver connection (prepare statement = #prepareStatement)"() {
    setup:
    Connection connection = null

    when:
    try {
      connection = new DummyThrowingConnection()
    } catch (Exception e) {
      connection = driverClass.connect(url, null)
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
    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0

    def tags = span.context().tags
    tags["db.type"] == driver
    tags["db.user"] == user
    tags["span.kind"] == "client"
    if (prepareStatement) {
      tags["component"] == "java-jdbc-prepared_statement"
    } else {
      tags["component"] == "java-jdbc-statement"
    }

    tags["db.jdbc.url"].contains(driver)
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
    prepareStatement | driver   | driverClass          | url                                            | user  | query
    true             | "h2"     | new Driver()         | "jdbc:h2:mem:" + dbName                        | null  | "SELECT 3;"
    true             | "derby"  | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP" | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    false            | "h2"     | new Driver()         | "jdbc:h2:mem:" + dbName                        | null  | "SELECT 3;"
    false            | "derby"  | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP" | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

}

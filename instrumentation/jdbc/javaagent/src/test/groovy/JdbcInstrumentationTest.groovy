/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import com.mchange.v2.c3p0.ComboPooledDataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.jdbc.TestConnection
import io.opentelemetry.instrumentation.jdbc.TestDriver
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.jdbc.AgentCacheFactory
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.derby.jdbc.EmbeddedDataSource
import org.apache.derby.jdbc.EmbeddedDriver
import org.h2.Driver
import org.h2.jdbcx.JdbcDataSource
import org.hsqldb.jdbc.JDBCDriver
import spock.lang.Shared
import spock.lang.Unroll

import javax.sql.DataSource
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

@Unroll
class JdbcInstrumentationTest extends AgentInstrumentationSpecification {

  @Shared
  def dbName = "jdbcUnitTest"
  @Shared
  def dbNameLower = dbName.toLowerCase()

  @Shared
  private Map<String, String> jdbcUrls = [
    "h2"    : "jdbc:h2:mem:$dbName",
    "derby" : "jdbc:derby:memory:$dbName",
    "hsqldb": "jdbc:hsqldb:mem:$dbName",
  ]

  @Shared
  private Map<String, String> jdbcDriverClassNames = [
    "h2"    : "org.h2.Driver",
    "derby" : "org.apache.derby.jdbc.EmbeddedDriver",
    "hsqldb": "org.hsqldb.jdbc.JDBCDriver",
  ]

  @Shared
  private Map<String, String> jdbcUserNames = [
    "h2"    : null,
    "derby" : "APP",
    "hsqldb": "SA",
  ]

  @Shared
  private Properties connectionProps = {
    def props = new Properties()
//    props.put("user", "someUser")
//    props.put("password", "somePassword")
    props.put("databaseName", "someDb")
    props.put("OPEN_NEW", "true") // So H2 doesn't complain about username/password.
    return props
  }()

  // JDBC Connection pool name (i.e. HikariCP) -> Map<dbName, Datasource>
  @Shared
  private Map<String, Map<String, DataSource>> cpDatasources = new HashMap<>()

  def prepareConnectionPoolDatasources() {
    String[] connectionPoolNames = [
      "tomcat", "hikari", "c3p0",
    ]
    connectionPoolNames.each {
      cpName ->
        Map<String, DataSource> dbDSMapping = new HashMap<>()
        jdbcUrls.each {
          dbType, jdbcUrl ->
            dbDSMapping.put(dbType, createDS(cpName, dbType, jdbcUrl))
        }
        cpDatasources.put(cpName, dbDSMapping)
    }
  }

  def createTomcatDS(String dbType, String jdbcUrl) {
    DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource()
    def jdbcUrlToSet = dbType == "derby" ? jdbcUrl + ";create=true" : jdbcUrl
    ds.setUrl(jdbcUrlToSet)
    ds.setDriverClassName(jdbcDriverClassNames.get(dbType))
    String username = jdbcUserNames.get(dbType)
    if (username != null) {
      ds.setUsername(username)
    }
    ds.setPassword("")
    ds.setMaxActive(1) // to test proper caching, having > 1 max active connection will be hard to
    // determine whether the connection is properly cached
    return ds
  }

  def createHikariDS(String dbType, String jdbcUrl) {
    HikariConfig config = new HikariConfig()
    def jdbcUrlToSet = dbType == "derby" ? jdbcUrl + ";create=true" : jdbcUrl
    config.setJdbcUrl(jdbcUrlToSet)
    String username = jdbcUserNames.get(dbType)
    if (username != null) {
      config.setUsername(username)
    }
    config.setPassword("")
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    config.setMaximumPoolSize(1)

    return new HikariDataSource(config)
  }

  def createC3P0DS(String dbType, String jdbcUrl) {
    DataSource ds = new ComboPooledDataSource()
    ds.setDriverClass(jdbcDriverClassNames.get(dbType))
    def jdbcUrlToSet = dbType == "derby" ? jdbcUrl + ";create=true" : jdbcUrl
    ds.setJdbcUrl(jdbcUrlToSet)
    String username = jdbcUserNames.get(dbType)
    if (username != null) {
      ds.setUser(username)
    }
    ds.setPassword("")
    ds.setMaxPoolSize(1)
    return ds
  }

  def createDS(String connectionPoolName, String dbType, String jdbcUrl) {
    DataSource ds = null
    if (connectionPoolName == "tomcat") {
      ds = createTomcatDS(dbType, jdbcUrl)
    }
    if (connectionPoolName == "hikari") {
      ds = createHikariDS(dbType, jdbcUrl)
    }
    if (connectionPoolName == "c3p0") {
      ds = createC3P0DS(dbType, jdbcUrl)
    }
    return ds
  }

  def setupSpec() {
    prepareConnectionPoolDatasources()
  }

  def cleanupSpec() {
    cpDatasources.values().each {
      it.values().each {
        datasource ->
          if (datasource instanceof Closeable) {
            datasource.close()
          }
      }
    }
  }

  def "basic statement with #connection.getClass().getCanonicalName() on #system generates spans"() {
    setup:
    Statement statement = connection.createStatement()
    ResultSet resultSet = runWithSpan("parent") {
      return statement.executeQuery(query)
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbNameLower
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" table
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system   | connection                                                           | username | query                                           | sanitizedQuery                                  | spanName                                 | url             | table
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), null)                       | null     | "SELECT 3"                                      | "SELECT ?"                                      | "SELECT $dbNameLower"                    | "h2:mem:"       | null
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null)            | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"                | "SELECT ? FROM SYSIBM.SYSDUMMY1"                | "SELECT SYSIBM.SYSDUMMY1"                | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null)               | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT INFORMATION_SCHEMA.SYSTEM_USERS" | "hsqldb:mem:"   | "INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), connectionProps)            | null     | "SELECT 3"                                      | "SELECT ?"                                      | "SELECT $dbNameLower"                    | "h2:mem:"       | null
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), connectionProps) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"                | "SELECT ? FROM SYSIBM.SYSDUMMY1"                | "SELECT SYSIBM.SYSDUMMY1"                | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), connectionProps)    | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT INFORMATION_SCHEMA.SYSTEM_USERS" | "hsqldb:mem:"   | "INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("tomcat").get("h2").getConnection()                | null     | "SELECT 3"                                      | "SELECT ?"                                      | "SELECT $dbNameLower"                    | "h2:mem:"       | null
    "derby"  | cpDatasources.get("tomcat").get("derby").getConnection()             | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"                | "SELECT ? FROM SYSIBM.SYSDUMMY1"                | "SELECT SYSIBM.SYSDUMMY1"                | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("tomcat").get("hsqldb").getConnection()            | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT INFORMATION_SCHEMA.SYSTEM_USERS" | "hsqldb:mem:"   | "INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("hikari").get("h2").getConnection()                | null     | "SELECT 3"                                      | "SELECT ?"                                      | "SELECT $dbNameLower"                    | "h2:mem:"       | null
    "derby"  | cpDatasources.get("hikari").get("derby").getConnection()             | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"                | "SELECT ? FROM SYSIBM.SYSDUMMY1"                | "SELECT SYSIBM.SYSDUMMY1"                | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("hikari").get("hsqldb").getConnection()            | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT INFORMATION_SCHEMA.SYSTEM_USERS" | "hsqldb:mem:"   | "INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("c3p0").get("h2").getConnection()                  | null     | "SELECT 3"                                      | "SELECT ?"                                      | "SELECT $dbNameLower"                    | "h2:mem:"       | null
    "derby"  | cpDatasources.get("c3p0").get("derby").getConnection()               | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"                | "SELECT ? FROM SYSIBM.SYSDUMMY1"                | "SELECT SYSIBM.SYSDUMMY1"                | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("c3p0").get("hsqldb").getConnection()              | "SA"     | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS" | "SELECT INFORMATION_SCHEMA.SYSTEM_USERS" | "hsqldb:mem:"   | "INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  def "prepared statement execute on #system with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = runWithSpan("parent") {
      assert statement.execute()
      return statement.resultSet
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbNameLower
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" table
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system  | connection                                                | username | query                            | sanitizedQuery                   | spanName                  | url             | table
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
  }

  def "prepared statement query on #system with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = runWithSpan("parent") {
      return statement.executeQuery()
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbNameLower
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" table
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system  | connection                                                | username | query                            | sanitizedQuery                   | spanName                  | url             | table
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
  }

  def "prepared call on #system with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    CallableStatement statement = connection.prepareCall(query)
    ResultSet resultSet = runWithSpan("parent") {
      return statement.executeQuery()
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbName.toLowerCase()
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" table
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system  | connection                                                | username | query                            | sanitizedQuery                   | spanName                  | url             | table
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"                       | "SELECT ?"                       | "SELECT $dbNameLower"     | "h2:mem:"       | null
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
  }

  def "statement update on #system with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    Statement statement = connection.createStatement()
    def sql = connection.nativeSQL(query)

    expect:
    runWithSpan("parent") {
      return !statement.execute(sql)
    }
    statement.updateCount == 0
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name dbNameLower
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbNameLower
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_STATEMENT.key" query
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system   | connection                                                | username | query                                                                           | url
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "CREATE TABLE S_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"                   | "h2:mem:"
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "CREATE TABLE S_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"                | "derby:memory:"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null)    | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"        | "hsqldb:mem:"
    "h2"     | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "CREATE TABLE S_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"            | "h2:mem:"
    "derby"  | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "CREATE TABLE S_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"         | "derby:memory:"
    "hsqldb" | cpDatasources.get("tomcat").get("hsqldb").getConnection() | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))" | "hsqldb:mem:"
    "h2"     | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "CREATE TABLE S_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"            | "h2:mem:"
    "derby"  | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "CREATE TABLE S_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"         | "derby:memory:"
    "hsqldb" | cpDatasources.get("hikari").get("hsqldb").getConnection() | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))" | "hsqldb:mem:"
    "h2"     | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "CREATE TABLE S_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"              | "h2:mem:"
    "derby"  | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "CREATE TABLE S_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"           | "derby:memory:"
    "hsqldb" | cpDatasources.get("c3p0").get("hsqldb").getConnection()   | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"   | "hsqldb:mem:"
  }

  def "prepared statement update on #system with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    def sql = connection.nativeSQL(query)
    PreparedStatement statement = connection.prepareStatement(sql)

    expect:
    runWithSpan("parent") {
      return statement.executeUpdate() == 0
    }
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name dbNameLower
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbName.toLowerCase()
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_STATEMENT.key" query
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    system  | connection                                                | username | query                                                                    | url
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "CREATE TABLE PS_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"           | "h2:mem:"
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "CREATE TABLE PS_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"        | "derby:memory:"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "CREATE TABLE PS_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"    | "h2:mem:"
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "CREATE TABLE PS_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))" | "derby:memory:"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "CREATE TABLE PS_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"    | "h2:mem:"
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "CREATE TABLE PS_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))" | "derby:memory:"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "CREATE TABLE PS_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"      | "h2:mem:"
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "CREATE TABLE PS_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"   | "derby:memory:"
  }

  def "connection constructor throwing then generating correct spans after recovery using #driver connection (prepare statement = #prepareStatement)"() {
    setup:
    Connection connection = null

    when:
    try {
      connection = new TestConnection(true)
      connection.url = "jdbc:testdb://localhost"
    } catch (Exception ignored) {
      connection = driver.connect(jdbcUrl, null)
    }

    def (Statement statement, ResultSet rs) = runWithSpan("parent") {
      if (prepareStatement) {
        def statement = connection.prepareStatement(query)
        return new Tuple(statement, statement.executeQuery())
      }

      def statement = connection.createStatement()
      return new Tuple(statement, statement.executeQuery(query))
    }

    then:
    rs.next()
    rs.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" system
            "$SemanticAttributes.DB_NAME.key" dbNameLower
            if (username != null) {
              "$SemanticAttributes.DB_USER.key" username
            }
            "$SemanticAttributes.DB_CONNECTION_STRING.key" url
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" table
          }
        }
      }
    }

    cleanup:
    statement?.close()
    connection?.close()

    where:
    prepareStatement | system  | driver               | jdbcUrl                                        | username | query                            | sanitizedQuery                   | spanName                  | url             | table
    true             | "h2"    | new Driver()         | "jdbc:h2:mem:" + dbName                        | null     | "SELECT 3;"                      | "SELECT ?;"                      | "SELECT $dbNameLower"     | "h2:mem:"       | null
    true             | "derby" | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
    false            | "h2"    | new Driver()         | "jdbc:h2:mem:" + dbName                        | null     | "SELECT 3;"                      | "SELECT ?;"                      | "SELECT $dbNameLower"     | "h2:mem:"       | null
    false            | "derby" | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1" | "SELECT ? FROM SYSIBM.SYSDUMMY1" | "SELECT SYSIBM.SYSDUMMY1" | "derby:memory:" | "SYSIBM.SYSDUMMY1"
  }

  def "calling #datasource.class.simpleName getConnection generates a span when under existing trace"() {
    setup:
    assert datasource instanceof DataSource
    init?.call(datasource)

    when:
    datasource.getConnection().close()

    then:
    !traces.any { it.any { it.name == "database.connection" } }
    clearExportedData()

    when:
    runWithSpan("parent") {
      datasource.getConnection().close()
    }

    then:
    assertTraces(1) {
      trace(0, recursive ? 3 : 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }

        span(1) {
          name "${datasource.class.simpleName}.getConnection"
          kind INTERNAL
          childOf span(0)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE.key" datasource.class.name
            "$SemanticAttributes.CODE_FUNCTION.key" "getConnection"
          }
        }
        if (recursive) {
          span(2) {
            name "${datasource.class.simpleName}.getConnection"
            kind INTERNAL
            childOf span(1)
            attributes {
              "$SemanticAttributes.CODE_NAMESPACE.key" datasource.class.name
              "$SemanticAttributes.CODE_FUNCTION.key" "getConnection"
            }
          }
        }
      }
    }

    where:
    datasource                               | init
    new JdbcDataSource()                     | { ds -> ds.setURL(jdbcUrls.get("h2")) }
    new EmbeddedDataSource()                 | { ds -> ds.jdbcurl = jdbcUrls.get("derby") }
    cpDatasources.get("hikari").get("h2")    | null
    cpDatasources.get("hikari").get("derby") | null
    cpDatasources.get("c3p0").get("h2")      | null
    cpDatasources.get("c3p0").get("derby")   | null

    // Tomcat's pool doesn't work because the getConnection method is
    // implemented in a parent class that doesn't implement DataSource

    recursive = datasource instanceof EmbeddedDataSource
  }

  def "test getClientInfo exception"() {
    setup:
    Connection connection = new TestConnection(false)
    connection.url = "jdbc:testdb://localhost"

    when:
    Statement statement = null
    runWithSpan("parent") {
      statement = connection.createStatement()
      return statement.executeQuery(query)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "DB Query"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "testdb"
            "$SemanticAttributes.DB_STATEMENT.key" "testing ?"
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "testdb://localhost"
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
          }
        }
      }
    }

    cleanup:
    statement?.close()
    connection?.close()

    where:
    query = "testing 123"
  }

  def "should produce proper span name #spanName"() {
    setup:
    def driver = new TestDriver()

    when:
    def connection = driver.connect(url, null)
    runWithSpan("parent") {
      def statement = connection.createStatement()
      return statement.executeQuery(query)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name spanName
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "testdb"
            "$SemanticAttributes.DB_NAME.key" databaseName
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "testdb://localhost"
            "$SemanticAttributes.DB_STATEMENT.key" sanitizedQuery
            "$SemanticAttributes.DB_OPERATION.key" operation
            "$SemanticAttributes.DB_SQL_TABLE.key" table
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
          }
        }
      }
    }

    where:
    url                                         | query                 | sanitizedQuery        | spanName            | databaseName | operation | table
    "jdbc:testdb://localhost?databaseName=test" | "SELECT * FROM table" | "SELECT * FROM table" | "SELECT test.table" | "test"       | "SELECT"  | "table"
    "jdbc:testdb://localhost?databaseName=test" | "SELECT 42"           | "SELECT ?"            | "SELECT test"       | "test"       | "SELECT"  | null
    "jdbc:testdb://localhost"                   | "SELECT * FROM table" | "SELECT * FROM table" | "SELECT table"      | null         | "SELECT"  | "table"
    "jdbc:testdb://localhost?databaseName=test" | "CREATE TABLE table"  | "CREATE TABLE table"  | "test"              | "test"       | null      | null
    "jdbc:testdb://localhost"                   | "CREATE TABLE table"  | "CREATE TABLE table"  | "DB Query"          | null         | null      | null
  }

  def "#connectionPoolName connections should be cached in case of wrapped connections"() {
    setup:
    String dbType = "hsqldb"
    DataSource ds = createDS(connectionPoolName, dbType, jdbcUrls.get(dbType))
    String query = "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    int numQueries = 5
    Connection connection = null
    int[] res = new int[numQueries]

    when:
    for (int i = 0; i < numQueries; ++i) {
      try {
        connection = ds.getConnection()
        def statement = connection.prepareStatement(query)
        def rs = statement.executeQuery()
        if (rs.next()) {
          res[i] = rs.getInt(1)
        } else {
          res[i] = 0
        }
      } finally {
        connection.close()
      }
    }

    then:
    for (int i = 0; i < numQueries; ++i) {
      res[i] == 3
    }
    assertTraces(numQueries) {
      for (int i = 0; i < numQueries; ++i) {
        trace(i, 1) {
          span(0) {
            name "SELECT INFORMATION_SCHEMA.SYSTEM_USERS"
            kind CLIENT
            attributes {
              "$SemanticAttributes.DB_SYSTEM.key" "hsqldb"
              "$SemanticAttributes.DB_NAME.key" dbNameLower
              "$SemanticAttributes.DB_USER.key" "SA"
              "$SemanticAttributes.DB_CONNECTION_STRING.key" "hsqldb:mem:"
              "$SemanticAttributes.DB_STATEMENT.key" "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS"
              "$SemanticAttributes.DB_OPERATION.key" "SELECT"
              "$SemanticAttributes.DB_SQL_TABLE.key" "INFORMATION_SCHEMA.SYSTEM_USERS"
            }
          }
        }
      }
    }

    cleanup:
    if (ds instanceof Closeable) {
      ds.close()
    }

    where:
    connectionPoolName | _
    "hikari"           | _
    "tomcat"           | _
    "c3p0"             | _
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2644
  def "should handle recursive Statements inside Connection.getMetaData(): #desc"() {
    given:
    def connection = new DbCallingConnection(usePreparedStatementInConnection)
    connection.url = "jdbc:testdb://localhost"

    when:
    runWithSpan("parent") {
      executeQueryFunction(connection, "SELECT * FROM table")
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SELECT table"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "testdb"
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "testdb://localhost"
            "$SemanticAttributes.DB_STATEMENT.key" "SELECT * FROM table"
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" "table"
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
          }
        }
      }
    }

    where:
    desc                                                           | usePreparedStatementInConnection | executeQueryFunction
    "getMetaData() uses Statement, test Statement"                 | false                            | { con, query -> con.createStatement().executeQuery(query) }
    "getMetaData() uses PreparedStatement, test Statement"         | true                             | { con, query -> con.createStatement().executeQuery(query) }
    "getMetaData() uses Statement, test PreparedStatement"         | false                            | { con, query -> con.prepareStatement(query).executeQuery() }
    "getMetaData() uses PreparedStatement, test PreparedStatement" | true                             | { con, query -> con.prepareStatement(query).executeQuery() }
  }

  def "should use agent data store"() {
    setup:
    Class<?> clazz = Class.forName("io.opentelemetry.javaagent.shaded.instrumentation.jdbc.internal.JdbcData")
    Field field = clazz.getDeclaredField("cacheFactory")
    field.setAccessible(true)
    def dataStoreFactory = field.get(null)

    expect:
    dataStoreFactory.getClass() == AgentCacheFactory
  }

  class DbCallingConnection extends TestConnection {
    final boolean usePreparedStatement

    DbCallingConnection(boolean usePreparedStatement) {
      super(false)
      this.usePreparedStatement = usePreparedStatement
    }

    @Override
    DatabaseMetaData getMetaData() throws SQLException {
      // simulate retrieving DB metadata from the DB itself
      if (usePreparedStatement) {
        prepareStatement("SELECT * from DB_METADATA").executeQuery()
      } else {
        createStatement().executeQuery("SELECT * from DB_METADATA")
      }
      return super.getMetaData()
    }
  }
}

/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.mchange.v2.c3p0.ComboPooledDataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.derby.jdbc.EmbeddedDataSource
import org.apache.derby.jdbc.EmbeddedDriver
import org.h2.Driver
import org.h2.jdbcx.JdbcDataSource
import org.hsqldb.jdbc.JDBCDriver
import spock.lang.Shared
import spock.lang.Unroll
import test.TestConnection
import test.TestStatement

import javax.sql.DataSource
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT

class JDBCInstrumentationTest extends AgentTestRunner {
  static {
    System.setProperty("ota.integration.jdbc-datasource.enabled", "true")
  }

  @Shared
  def dbName = "jdbcUnitTest"

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

  @Unroll
  def "basic statement with #connection.getClass().getCanonicalName() on #driver generates spans"() {
    setup:
    Statement statement = connection.createStatement()
    ResultSet resultSet = runUnderTrace("parent") {
      withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
        return statement.executeQuery(query)
      }
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" renameService ? dbName.toLowerCase() : driver
            "$Tags.COMPONENT" "java-jdbc-statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver   | connection                                                           | username | renameService | query
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), null)                       | null     | false         | "SELECT 3"
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null)            | "APP"    | false         | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null)               | "SA"     | false         | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), connectionProps)            | null     | true          | "SELECT 3"
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), connectionProps) | "APP"    | true          | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), connectionProps)    | "SA"     | true          | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("tomcat").get("h2").getConnection()                | null     | false         | "SELECT 3"
    "derby"  | cpDatasources.get("tomcat").get("derby").getConnection()             | "APP"    | false         | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("tomcat").get("hsqldb").getConnection()            | "SA"     | true          | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("hikari").get("h2").getConnection()                | null     | false         | "SELECT 3"
    "derby"  | cpDatasources.get("hikari").get("derby").getConnection()             | "APP"    | true          | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("hikari").get("hsqldb").getConnection()            | "SA"     | false         | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    "h2"     | cpDatasources.get("c3p0").get("h2").getConnection()                  | null     | true          | "SELECT 3"
    "derby"  | cpDatasources.get("c3p0").get("derby").getConnection()               | "APP"    | false         | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | cpDatasources.get("c3p0").get("hsqldb").getConnection()              | "SA"     | false         | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "prepared statement execute on #driver with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = runUnderTrace("parent") {
      assert statement.execute()
      return statement.resultSet
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver  | connection                                                | username | query
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

  @Unroll
  def "prepared statement query on #driver with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = runUnderTrace("parent") {
      return statement.executeQuery()
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver  | connection                                                | username | query
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

  @Unroll
  def "prepared call on #driver with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    CallableStatement statement = connection.prepareCall(query)
    ResultSet resultSet = runUnderTrace("parent") {
      return statement.executeQuery()
    }

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver  | connection                                                | username | query
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "SELECT 3"
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "SELECT 3"
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "SELECT 3"
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

  @Unroll
  def "statement update on #driver with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    Statement statement = connection.createStatement()
    def sql = connection.nativeSQL(query)

    expect:
    runUnderTrace("parent") {
      return !statement.execute(sql)
    }
    statement.updateCount == 0
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            "$Tags.COMPONENT" "java-jdbc-statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver   | connection                                                | username | query
    "h2"     | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "CREATE TABLE S_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "CREATE TABLE S_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null)    | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"     | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "CREATE TABLE S_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "CREATE TABLE S_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | cpDatasources.get("tomcat").get("hsqldb").getConnection() | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"     | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "CREATE TABLE S_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "CREATE TABLE S_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | cpDatasources.get("hikari").get("hsqldb").getConnection() | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"     | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "CREATE TABLE S_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "CREATE TABLE S_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | cpDatasources.get("c3p0").get("hsqldb").getConnection()   | "SA"     | "CREATE TABLE PUBLIC.S_HSQLDB_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }

  @Unroll
  def "prepared statement update on #driver with #connection.getClass().getCanonicalName() generates a span"() {
    setup:
    def sql = connection.nativeSQL(query)
    PreparedStatement statement = connection.prepareStatement(sql)

    expect:
    runUnderTrace("parent") {
      return statement.executeUpdate() == 0
    }
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()

    where:
    driver  | connection                                                | username | query
    "h2"    | new Driver().connect(jdbcUrls.get("h2"), null)            | null     | "CREATE TABLE PS_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby" | new EmbeddedDriver().connect(jdbcUrls.get("derby"), null) | "APP"    | "CREATE TABLE PS_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"    | cpDatasources.get("tomcat").get("h2").getConnection()     | null     | "CREATE TABLE PS_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby" | cpDatasources.get("tomcat").get("derby").getConnection()  | "APP"    | "CREATE TABLE PS_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"    | cpDatasources.get("hikari").get("h2").getConnection()     | null     | "CREATE TABLE PS_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby" | cpDatasources.get("hikari").get("derby").getConnection()  | "APP"    | "CREATE TABLE PS_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "h2"    | cpDatasources.get("c3p0").get("h2").getConnection()       | null     | "CREATE TABLE PS_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby" | cpDatasources.get("c3p0").get("derby").getConnection()    | "APP"    | "CREATE TABLE PS_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }

  @Unroll
  def "connection constructor throwing then generating correct spans after recovery using #driver connection (prepare statement = #prepareStatement)"() {
    setup:
    Connection connection = null

    when:
    try {
      connection = new TestConnection(true)
    } catch (Exception e) {
      connection = driverClass.connect(url, null)
    }

    Statement statement = null
    ResultSet rs = runUnderTrace("parent") {
      if (prepareStatement) {
        statement = connection.prepareStatement(query)
        return statement.executeQuery()
      }

      statement = connection.createStatement()
      return statement.executeQuery(query)
    }

    then:
    rs.next()
    rs.getInt(1) == 3
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" driver
            if (prepareStatement) {
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            } else {
              "$Tags.COMPONENT" "java-jdbc-statement"
            }
            "$Tags.DB_TYPE" driver
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            if (username != null) {
              "$Tags.DB_USER" username
            }
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
    }

    cleanup:
    if (statement != null) {
      statement.close()
    }
    if (connection != null) {
      connection.close()
    }

    where:
    prepareStatement | driver  | driverClass          | url                                            | username | query
    true             | "h2"    | new Driver()         | "jdbc:h2:mem:" + dbName                        | null     | "SELECT 3;"
    true             | "derby" | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    false            | "h2"    | new Driver()         | "jdbc:h2:mem:" + dbName                        | null     | "SELECT 3;"
    false            | "derby" | new EmbeddedDriver() | "jdbc:derby:memory:" + dbName + ";create=true" | "APP"    | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
  }

  def "calling #datasource.class.simpleName getConnection generates a span when under existing trace"() {
    setup:
    assert datasource instanceof DataSource
    init?.call(datasource)

    when:
    datasource.getConnection().close()

    then:
    !TEST_WRITER.traces.any { it.any { it.name == "database.connection" } }
    TEST_WRITER.clear()

    when:
    runUnderTrace("parent") {
      datasource.getConnection().close()
    }

    then:
    assertTraces(1) {
      trace(0, recursive ? 3 : 2) {
        basicSpan(it, 0, "parent")

        span(1) {
          operationName "${datasource.class.simpleName}.getConnection"
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "java-jdbc-connection"
          }
        }
        if (recursive) {
          span(2) {
            operationName "${datasource.class.simpleName}.getConnection"
            childOf span(1)
            tags {
              "$Tags.COMPONENT" "java-jdbc-connection"
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

    when:
    Statement statement = null
    runUnderTrace("parent") {
      statement = connection.createStatement()
      return statement.executeQuery(query)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName query
          spanKind CLIENT
          childOf span(0)
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" database
            "$Tags.COMPONENT" "java-jdbc-statement"
            "$Tags.DB_TYPE" database
            "$Tags.DB_STATEMENT" query
            "span.origin.type" TestStatement.name
          }
        }
      }
    }

    cleanup:
    if (statement != null) {
      statement.close()
    }
    if (connection != null) {
      connection.close()
    }

    where:
    database = "testdb"
    query = "testing 123"
  }

  @Unroll
  def "#connectionPoolName connections should be cached in case of wrapped connections"() {
    setup:
    String dbType = "hsqldb"
    DataSource ds = createDS(connectionPoolName, dbType, jdbcUrls.get(dbType))
    String query = "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
    int numQueries = 5
    Connection connection = null
    Statement statement = null
    ResultSet rs = null
    int[] res = new int[numQueries]

    when:
    for (int i = 0; i < numQueries; ++i) {
      try {
        connection = ds.getConnection()
        statement = connection.prepareStatement(query)
        rs = statement.executeQuery()
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
    assertTraces(5) {
      trace(0, 1) {
        span(0) {
          operationName query
          spanKind CLIENT
          errored false
          tags {
            "$MoreTags.SERVICE_NAME" dbType
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" dbType
            "$Tags.DB_INSTANCE" dbName.toLowerCase()
            "$Tags.DB_USER" "SA"
            "$Tags.DB_STATEMENT" query
            "span.origin.type" String
          }
        }
      }
      for (int i = 1; i < numQueries; ++i) {
        trace(i, 1) {
          span(0) {
            operationName query
            spanKind CLIENT
            errored false
            tags {
              "$MoreTags.SERVICE_NAME" dbType
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
              "$Tags.DB_TYPE" dbType
              "$Tags.DB_INSTANCE" dbName.toLowerCase()
              "$Tags.DB_USER" "SA"
              "$Tags.DB_STATEMENT" query
              "span.origin.type" String
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
}

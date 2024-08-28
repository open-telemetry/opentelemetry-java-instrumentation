/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.beans.PropertyVetoException;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.h2.Driver;
import org.hsqldb.jdbc.JDBCDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInstrumentationTest {

  @RegisterExtension
  static InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private String dbName;
  private String dbNameLower;
  private Map<String, String> jdbcUrls;
  private Map<String, String> jdbcDriverClassNames;
  private Map<String, String> jdbcUserNames;
  private Properties connectionProps;
  // JDBC Connection pool name (i.e. HikariCP) -> Map<dbName, Datasource>
  private Map<String, Map<String, DataSource>> cpDatasources;

  @BeforeAll
  public void setUp() {
    dbName = "jdbcUnitTest";
    dbNameLower = dbName.toLowerCase(Locale.ROOT);
    jdbcUrls =
        Collections.unmodifiableMap(
            Stream.of(
                    entry("h2", "jdbc:h2:mem:" + dbName),
                    entry("derby", "jdbc:derby:memory:" + dbName),
                    entry("hsqldb", "jdbc:hsqldb:mem:" + dbName))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    jdbcDriverClassNames =
        Collections.unmodifiableMap(
            Stream.of(
                    entry("h2", "org.h2.Driver"),
                    entry("derby", "org.apache.derby.jdbc.EmbeddedDriver"),
                    entry("hsqldb", "org.hsqldb.jdbc.JDBCDriver"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    jdbcUserNames = new HashMap<>();
    jdbcUserNames.put("derby", "APP");
    jdbcUserNames.put("h2", null);
    jdbcUserNames.put("hsqldb", "SA");

    connectionProps = new Properties();
    connectionProps.put("databaseName", "someDb");
    connectionProps.put("OPEN_NEW", "true"); // So H2 doesn't complain about username/password.

    cpDatasources = new HashMap<>();

    prepareConnectionPoolDatasources();
    System.out.println("before all");
  }

  @AfterAll
  public void tearDown() {
    System.out.println("after all");
    cpDatasources
        .values()
        .forEach(
            k ->
                k.values()
                    .forEach(
                        dataSource -> {
                          if (dataSource instanceof Closeable) {
                            try {
                              ((Closeable) dataSource).close();
                            } catch (IOException e) {
                              // ignore
                            }
                          }
                        }));
  }

  void prepareConnectionPoolDatasources() {
    List<String> connectionPoolNames = asList("tomcat", "hikari", "c3p0");
    connectionPoolNames.forEach(
        cpName -> {
          Map<String, DataSource> dbDSMapping = new HashMap<>();
          jdbcUrls.forEach(
              (dbType, jdbcUrl) -> dbDSMapping.put(dbType, createDS(cpName, dbType, jdbcUrl)));
          cpDatasources.put(cpName, dbDSMapping);
        });
  }

  DataSource createTomcatDS(String dbType, String jdbcUrl) {
    org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
    String jdbcUrlToSet = Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl;
    ds.setUrl(jdbcUrlToSet);
    ds.setDriverClassName(jdbcDriverClassNames.get(dbType));
    String username = jdbcUserNames.get(dbType);
    if (username != null) {
      ds.setUsername(username);
    }
    ds.setPassword("");
    ds.setMaxActive(1); // to test proper caching, having > 1 max active connection will be hard to
    // determine whether the connection is properly cached
    return ds;
  }

  DataSource createHikariDS(String dbType, String jdbcUrl) {
    HikariConfig config = new HikariConfig();
    String jdbcUrlToSet = Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl;
    config.setJdbcUrl(jdbcUrlToSet);
    String username = jdbcUserNames.get(dbType);
    if (username != null) {
      config.setUsername(username);
    }
    config.setPassword("");
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.setMaximumPoolSize(1);

    return new HikariDataSource(config);
  }

  DataSource createC3P0DS(String dbType, String jdbcUrl) {
    ComboPooledDataSource ds = new ComboPooledDataSource();
    try {
      ds.setDriverClass(jdbcDriverClassNames.get(dbType));
    } catch (PropertyVetoException e) {
      throw new RuntimeException(e);
    }
    String jdbcUrlToSet = Objects.equals(dbType, "derby") ? jdbcUrl + ";create=true" : jdbcUrl;
    ds.setJdbcUrl(jdbcUrlToSet);
    String username = jdbcUserNames.get(dbType);
    if (username != null) {
      ds.setUser(username);
    }
    ds.setPassword("");
    ds.setMaxPoolSize(1);
    return ds;
  }

  DataSource createDS(String connectionPoolName, String dbType, String jdbcUrl) {
    DataSource ds = null;
    if (Objects.equals(connectionPoolName, "tomcat")) {
      ds = createTomcatDS(dbType, jdbcUrl);
    }
    if (Objects.equals(connectionPoolName, "hikari")) {
      ds = createHikariDS(dbType, jdbcUrl);
    }
    if (Objects.equals(connectionPoolName, "c3p0")) {
      ds = createC3P0DS(dbType, jdbcUrl);
    }
    return ds;
  }

  Stream<Arguments> basicStatementStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new Driver().connect(jdbcUrls.get("h2"), null),
            null,
            "SELECT 3",
            "SELECT ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), null),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"),
        Arguments.of(
            "h2",
            new Driver().connect(jdbcUrls.get("h2"), connectionProps),
            null,
            "SELECT 3",
            "SELECT ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), connectionProps),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            new JDBCDriver().connect(jdbcUrls.get("hsqldb"), connectionProps),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"),
        Arguments.of(
            "h2",
            cpDatasources.get("tomcat").get("h2").getConnection(),
            null,
            "SELECT 3",
            "SELECT ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            cpDatasources.get("tomcat").get("derby").getConnection(),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("tomcat").get("hsqldb").getConnection(),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"),
        Arguments.of(
            "h2",
            cpDatasources.get("hikari").get("h2").getConnection(),
            null,
            "SELECT 3",
            "SELECT ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            cpDatasources.get("hikari").get("derby").getConnection(),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("hikari").get("hsqldb").getConnection(),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"),
        Arguments.of(
            "h2",
            cpDatasources.get("c3p0").get("h2").getConnection(),
            null,
            "SELECT 3",
            "SELECT ?",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            "derby",
            cpDatasources.get("c3p0").get("derby").getConnection(),
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("c3p0").get("hsqldb").getConnection(),
            "SA",
            "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS",
            "SELECT INFORMATION_SCHEMA.SYSTEM_USERS",
            "hsqldb:mem:",
            "INFORMATION_SCHEMA.SYSTEM_USERS"));
  }

  @SuppressWarnings("deprecation") // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
  @DisplayName(
      "basic statement with #connection.getClass().getCanonicalName() on #system generates spans")
  @ParameterizedTest
  @MethodSource("basicStatementStream")
  public void testBasicStatement(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    Statement statement = connection.createStatement();
    ResultSet resultSet = testing.runWithSpan("parent", () -> statement.executeQuery(query));

    resultSet.next();
    assertThat(resultSet.getInt(1)).isEqualTo(3);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, system),
                            equalTo(DbIncubatingAttributes.DB_NAME, dbNameLower),
                            satisfies(
                                DbIncubatingAttributes.DB_USER,
                                val ->
                                    val.satisfiesAnyOf(
                                        v -> assertThat(v).isEqualTo(username),
                                        v -> assertThat(v).isNull())),
                            equalTo(DbIncubatingAttributes.DB_CONNECTION_STRING, url),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, sanitizedQuery),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
                            equalTo(DbIncubatingAttributes.DB_SQL_TABLE, table))));
    statement.close();
    connection.close();
  }
}

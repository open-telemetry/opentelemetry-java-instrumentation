/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.TestDriver;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.beans.PropertyVetoException;
import java.io.Closeable;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.h2.jdbcx.JdbcDataSource;
import org.hsqldb.jdbc.JDBCDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class JdbcInstrumentationTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String dbName = "jdbcUnitTest";
  private static final String dbNameLower = dbName.toLowerCase(Locale.ROOT);
  private static final Map<String, String> jdbcUrls =
      ImmutableMap.of(
          "h2", "jdbc:h2:mem:" + dbName,
          "derby", "jdbc:derby:memory:" + dbName,
          "hsqldb", "jdbc:hsqldb:mem:" + dbName);
  private static final Map<String, String> jdbcDriverClassNames =
      ImmutableMap.of(
          "h2", "org.h2.Driver",
          "derby", "org.apache.derby.jdbc.EmbeddedDriver",
          "hsqldb", "org.hsqldb.jdbc.JDBCDriver");
  private static final Map<String, String> jdbcUserNames = Maps.newHashMap();
  private static final Properties connectionProps = new Properties();
  // JDBC Connection pool name (i.e. HikariCP) -> Map<dbName, Datasource>
  private static final Map<String, Map<String, DataSource>> cpDatasources = Maps.newHashMap();

  static {
    jdbcUserNames.put("derby", "APP");
    jdbcUserNames.put("h2", null);
    jdbcUserNames.put("hsqldb", "SA");

    connectionProps.put("databaseName", "someDb");
    connectionProps.put("OPEN_NEW", "true"); // So H2 doesn't complain about username/password.
  }

  @BeforeAll
  static void setUp() {
    prepareConnectionPoolDatasources();
  }

  @AfterAll
  static void tearDown() {
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
                            } catch (IOException ignore) {
                              // ignore
                            }
                          }
                        }));
  }

  static void prepareConnectionPoolDatasources() {
    List<String> connectionPoolNames = asList("tomcat", "hikari", "c3p0");
    connectionPoolNames.forEach(
        cpName -> {
          Map<String, DataSource> dbDsMapping = new HashMap<>();
          jdbcUrls.forEach(
              (dbType, jdbcUrl) -> dbDsMapping.put(dbType, createDs(cpName, dbType, jdbcUrl)));
          cpDatasources.put(cpName, dbDsMapping);
        });
  }

  static DataSource createTomcatDs(String dbType, String jdbcUrl) {
    org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
    String jdbcUrlToSet = dbType.equals("derby") ? jdbcUrl + ";create=true" : jdbcUrl;
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

  static DataSource createHikariDs(String dbType, String jdbcUrl) {
    HikariConfig config = new HikariConfig();
    String jdbcUrlToSet = dbType.equals("derby") ? jdbcUrl + ";create=true" : jdbcUrl;
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

  static DataSource createC3P0Ds(String dbType, String jdbcUrl) {
    ComboPooledDataSource ds = new ComboPooledDataSource();
    try {
      ds.setDriverClass(jdbcDriverClassNames.get(dbType));
    } catch (PropertyVetoException e) {
      throw new RuntimeException(e);
    }
    String jdbcUrlToSet = dbType.equals("derby") ? jdbcUrl + ";create=true" : jdbcUrl;
    ds.setJdbcUrl(jdbcUrlToSet);
    String username = jdbcUserNames.get(dbType);
    if (username != null) {
      ds.setUser(username);
    }
    ds.setPassword("");
    ds.setMaxPoolSize(1);
    return ds;
  }

  static DataSource createDs(String connectionPoolName, String dbType, String jdbcUrl) {
    DataSource ds = null;
    if (connectionPoolName.equals("tomcat")) {
      ds = createTomcatDs(dbType, jdbcUrl);
    }
    if (connectionPoolName.equals("hikari")) {
      ds = createHikariDs(dbType, jdbcUrl);
    }
    if (connectionPoolName.equals("c3p0")) {
      ds = createC3P0Ds(dbType, jdbcUrl);
    }
    return ds;
  }

  static Stream<Arguments> basicStatementStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new org.h2.Driver().connect(jdbcUrls.get("h2"), null),
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
            new org.h2.Driver().connect(jdbcUrls.get("h2"), connectionProps),
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
    cleanup.deferCleanup(statement);
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
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));

    if (table != null) {
      assertDurationMetric(
          testing,
          "io.opentelemetry.jdbc",
          DB_SYSTEM_NAME,
          DB_COLLECTION_NAME,
          DB_NAMESPACE,
          DB_OPERATION_NAME);
    } else {
      assertDurationMetric(
          testing, "io.opentelemetry.jdbc", DB_SYSTEM_NAME, DB_OPERATION_NAME, DB_NAMESPACE);
    }
  }

  static Stream<Arguments> preparedStatementStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new org.h2.Driver().connect(jdbcUrls.get("h2"), null),
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
            "SYSIBM.SYSDUMMY1"));
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testPreparedStatementExecute(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(query);
    cleanup.deferCleanup(statement);
    ResultSet resultSet =
        testing.runWithSpan(
            "parent",
            () -> {
              statement.execute();
              return statement.getResultSet();
            });

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
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testPreparedStatementQuery(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(query);
    cleanup.deferCleanup(statement);
    ResultSet resultSet = testing.runWithSpan("parent", () -> statement.executeQuery());

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
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  @ParameterizedTest
  @MethodSource("preparedStatementStream")
  void testPreparedCall(
      String system,
      Connection connection,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    CallableStatement statement = connection.prepareCall(query);
    cleanup.deferCleanup(statement);
    ResultSet resultSet = testing.runWithSpan("parent", () -> statement.executeQuery());

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
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  static Stream<Arguments> statementUpdateStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new org.h2.Driver().connect(jdbcUrls.get("h2"), null),
            null,
            "CREATE TABLE S_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_H2",
            "h2:mem:",
            "S_H2"),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), null),
            "APP",
            "CREATE TABLE S_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_DERBY",
            "derby:memory:",
            "S_DERBY"),
        Arguments.of(
            "hsqldb",
            new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null),
            "SA",
            "CREATE TABLE PUBLIC.S_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE PUBLIC.S_HSQLDB",
            "hsqldb:mem:",
            "PUBLIC.S_HSQLDB"),
        Arguments.of(
            "h2",
            cpDatasources.get("tomcat").get("h2").getConnection(),
            null,
            "CREATE TABLE S_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_H2_TOMCAT",
            "h2:mem:",
            "S_H2_TOMCAT"),
        Arguments.of(
            "derby",
            cpDatasources.get("tomcat").get("derby").getConnection(),
            "APP",
            "CREATE TABLE S_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_DERBY_TOMCAT",
            "derby:memory:",
            "S_DERBY_TOMCAT"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("tomcat").get("hsqldb").getConnection(),
            "SA",
            "CREATE TABLE PUBLIC.S_HSQLDB_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE PUBLIC.S_HSQLDB_TOMCAT",
            "hsqldb:mem:",
            "PUBLIC.S_HSQLDB_TOMCAT"),
        Arguments.of(
            "h2",
            cpDatasources.get("hikari").get("h2").getConnection(),
            null,
            "CREATE TABLE S_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_H2_HIKARI",
            "h2:mem:",
            "S_H2_HIKARI"),
        Arguments.of(
            "derby",
            cpDatasources.get("hikari").get("derby").getConnection(),
            "APP",
            "CREATE TABLE S_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_DERBY_HIKARI",
            "derby:memory:",
            "S_DERBY_HIKARI"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("hikari").get("hsqldb").getConnection(),
            "SA",
            "CREATE TABLE PUBLIC.S_HSQLDB_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE PUBLIC.S_HSQLDB_HIKARI",
            "hsqldb:mem:",
            "PUBLIC.S_HSQLDB_HIKARI"),
        Arguments.of(
            "h2",
            cpDatasources.get("c3p0").get("h2").getConnection(),
            null,
            "CREATE TABLE S_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_H2_C3P0",
            "h2:mem:",
            "S_H2_C3P0"),
        Arguments.of(
            "derby",
            cpDatasources.get("c3p0").get("derby").getConnection(),
            "APP",
            "CREATE TABLE S_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.S_DERBY_C3P0",
            "derby:memory:",
            "S_DERBY_C3P0"),
        Arguments.of(
            "hsqldb",
            cpDatasources.get("c3p0").get("hsqldb").getConnection(),
            "SA",
            "CREATE TABLE PUBLIC.S_HSQLDB_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE PUBLIC.S_HSQLDB_C3P0",
            "hsqldb:mem:",
            "PUBLIC.S_HSQLDB_C3P0"));
  }

  @ParameterizedTest
  @MethodSource("statementUpdateStream")
  void testStatementUpdate(
      String system,
      Connection connection,
      String username,
      String query,
      String spanName,
      String url,
      String table)
      throws SQLException {
    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    String sql = connection.nativeSQL(query);
    testing.runWithSpan("parent", () -> assertThat(statement.execute(sql)).isFalse());

    assertThat(statement.getUpdateCount()).isEqualTo(0);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), query),
                            equalTo(maybeStable(DB_OPERATION), "CREATE TABLE"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  static Stream<Arguments> preparedStatementUpdateStream() throws SQLException {
    return Stream.of(
        Arguments.of(
            "h2",
            new org.h2.Driver().connect(jdbcUrls.get("h2"), null),
            null,
            "CREATE TABLE PS_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_H2",
            "h2:mem:",
            "PS_H2"),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), null),
            "APP",
            "CREATE TABLE PS_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_DERBY",
            "derby:memory:",
            "PS_DERBY"),
        Arguments.of(
            "h2",
            cpDatasources.get("tomcat").get("h2").getConnection(),
            null,
            "CREATE TABLE PS_H2_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_H2_TOMCAT",
            "h2:mem:",
            "PS_H2_TOMCAT"),
        Arguments.of(
            "derby",
            cpDatasources.get("tomcat").get("derby").getConnection(),
            "APP",
            "CREATE TABLE PS_DERBY_TOMCAT (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_DERBY_TOMCAT",
            "derby:memory:",
            "PS_DERBY_TOMCAT"),
        Arguments.of(
            "h2",
            cpDatasources.get("hikari").get("h2").getConnection(),
            null,
            "CREATE TABLE PS_H2_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_H2_HIKARI",
            "h2:mem:",
            "PS_H2_HIKARI"),
        Arguments.of(
            "derby",
            cpDatasources.get("hikari").get("derby").getConnection(),
            "APP",
            "CREATE TABLE PS_DERBY_HIKARI (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_DERBY_HIKARI",
            "derby:memory:",
            "PS_DERBY_HIKARI"),
        Arguments.of(
            "h2",
            cpDatasources.get("c3p0").get("h2").getConnection(),
            null,
            "CREATE TABLE PS_H2_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_H2_C3P0",
            "h2:mem:",
            "PS_H2_C3P0"),
        Arguments.of(
            "derby",
            cpDatasources.get("c3p0").get("derby").getConnection(),
            "APP",
            "CREATE TABLE PS_DERBY_C3P0 (id INTEGER not NULL, PRIMARY KEY ( id ))",
            "CREATE TABLE jdbcunittest.PS_DERBY_C3P0",
            "derby:memory:",
            "PS_DERBY_C3P0"));
  }

  @ParameterizedTest
  @MethodSource("preparedStatementUpdateStream")
  void testPreparedStatementUpdate(
      String system,
      Connection connection,
      String username,
      String query,
      String spanName,
      String url,
      String table)
      throws SQLException {
    String sql = connection.nativeSQL(query);
    PreparedStatement statement = connection.prepareStatement(sql);
    cleanup.deferCleanup(statement);
    testing.runWithSpan("parent", () -> assertThat(statement.executeUpdate()).isEqualTo(0));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), query),
                            equalTo(maybeStable(DB_OPERATION), "CREATE TABLE"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  static Stream<Arguments> connectionConstructorStream() {
    return Stream.of(
        Arguments.of(
            true,
            "h2",
            new org.h2.Driver(),
            "jdbc:h2:mem:" + dbName,
            null,
            "SELECT 3;",
            "SELECT ?;",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            true,
            "derby",
            new EmbeddedDriver(),
            "jdbc:derby:memory:" + dbName + ";create=true",
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"),
        Arguments.of(
            false,
            "h2",
            new org.h2.Driver(),
            "jdbc:h2:mem:" + dbName,
            null,
            "SELECT 3;",
            "SELECT ?;",
            "SELECT " + dbNameLower,
            "h2:mem:",
            null),
        Arguments.of(
            false,
            "derby",
            new EmbeddedDriver(),
            "jdbc:derby:memory:" + dbName + ";create=true",
            "APP",
            "SELECT 3 FROM SYSIBM.SYSDUMMY1",
            "SELECT ? FROM SYSIBM.SYSDUMMY1",
            "SELECT SYSIBM.SYSDUMMY1",
            "derby:memory:",
            "SYSIBM.SYSDUMMY1"));
  }

  @SuppressWarnings("CatchingUnchecked")
  @ParameterizedTest
  @MethodSource("connectionConstructorStream")
  void testConnectionConstructorThrowing(
      boolean prepareStatement,
      String system,
      Driver driver,
      String jdbcUrl,
      String username,
      String query,
      String sanitizedQuery,
      String spanName,
      String url,
      String table)
      throws SQLException {
    Connection connection = null;

    try {
      connection = new TestConnection(true);
    } catch (Exception ignored) {
      connection = driver.connect(jdbcUrl, null);
    }
    cleanup.deferCleanup(connection);
    Connection finalConnection = connection;
    ResultSet rs =
        testing.runWithSpan(
            "parent",
            () -> {
              if (prepareStatement) {
                PreparedStatement stmt = finalConnection.prepareStatement(query);
                cleanup.deferCleanup(stmt);
                return stmt.executeQuery();
              } else {
                Statement stmt = finalConnection.createStatement();
                cleanup.deferCleanup(stmt);
                return stmt.executeQuery(query);
              }
            });

    rs.next();
    assertThat(rs.getInt(1)).isEqualTo(3);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), table))));
  }

  static Stream<Arguments> getConnectionStream() {
    return Stream.of(
        Arguments.of(
            new JdbcDataSource(),
            (Consumer<DataSource>) ds -> ((JdbcDataSource) ds).setUrl(jdbcUrls.get("h2")),
            "h2",
            null,
            "h2:mem:"),
        Arguments.of(
            new EmbeddedDataSource(),
            (Consumer<DataSource>)
                ds -> ((EmbeddedDataSource) ds).setDatabaseName("memory:" + dbName),
            "derby",
            "APP",
            "derby:memory:"),
        Arguments.of(cpDatasources.get("hikari").get("h2"), null, "h2", null, "h2:mem:"),
        Arguments.of(
            cpDatasources.get("hikari").get("derby"), null, "derby", "APP", "derby:memory:"),
        Arguments.of(cpDatasources.get("c3p0").get("h2"), null, "h2", null, "h2:mem:"),
        Arguments.of(
            cpDatasources.get("c3p0").get("derby"), null, "derby", "APP", "derby:memory:"));
  }

  @ParameterizedTest(autoCloseArguments = false)
  @MethodSource("getConnectionStream")
  void testGetConnection(
      DataSource datasource,
      Consumer<DataSource> init,
      String system,
      String user,
      String connectionString)
      throws SQLException {
    // Tomcat's pool doesn't work because the getConnection method is
    // implemented in a parent class that doesn't implement DataSource
    boolean recursive = datasource instanceof EmbeddedDataSource;

    if (init != null) {
      init.accept(datasource);
    }
    datasource.getConnection().close();
    assertThat(testing.spans()).noneMatch(span -> span.getName().equals("database.connection"));

    testing.clearData();

    testing.runWithSpan("parent", () -> datasource.getConnection().close());
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions =
              new ArrayList<>(
                  asList(
                      span1 -> span1.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                      span1 ->
                          span1
                              .hasName(datasource.getClass().getSimpleName() + ".getConnection")
                              .hasKind(SpanKind.INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(
                                      CodeIncubatingAttributes.CODE_NAMESPACE,
                                      datasource.getClass().getName()),
                                  equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "getConnection"),
                                  equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                                  equalTo(DB_USER, emitStableDatabaseSemconv() ? null : user),
                                  equalTo(maybeStable(DB_NAME), "jdbcunittest"),
                                  equalTo(
                                      DB_CONNECTION_STRING,
                                      emitStableDatabaseSemconv() ? null : connectionString))));
          if (recursive) {
            assertions.add(
                span ->
                    span.hasName(datasource.getClass().getSimpleName() + ".getConnection")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                datasource.getClass().getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "getConnection"),
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : user),
                            equalTo(maybeStable(DB_NAME), "jdbcunittest"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : connectionString)));
          }
          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  @ParameterizedTest
  @DisplayName("test getClientInfo exception")
  @ValueSource(strings = "testing 123")
  void testGetClientInfoException(String query) throws SQLException {
    TestConnection connection = new TestConnection(false);
    cleanup.deferCleanup(connection);
    connection.setUrl("jdbc:testdb://localhost");

    Statement statement =
        testing.runWithSpan(
            "parent",
            () -> {
              Statement stmt = connection.createStatement();
              stmt.executeQuery(query);
              return stmt;
            });
    cleanup.deferCleanup(statement);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("DB Query")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "other_sql"),
                            equalTo(maybeStable(DB_STATEMENT), "testing ?"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "testdb://localhost"),
                            equalTo(SERVER_ADDRESS, "localhost"))));
  }

  static Stream<Arguments> spanNameStream() {
    return Stream.of(
        Arguments.of(
            "jdbc:testdb://localhost?databaseName=test",
            "SELECT * FROM table",
            "SELECT * FROM table",
            "SELECT test.table",
            "test",
            "SELECT",
            "table"),
        Arguments.of(
            "jdbc:testdb://localhost?databaseName=test",
            "SELECT 42",
            "SELECT ?",
            "SELECT test",
            "test",
            "SELECT",
            null),
        Arguments.of(
            "jdbc:testdb://localhost",
            "SELECT * FROM table",
            "SELECT * FROM table",
            "SELECT table",
            null,
            "SELECT",
            "table"),
        Arguments.of(
            "jdbc:testdb://localhost?databaseName=test",
            "CREATE TABLE table",
            "CREATE TABLE table",
            "CREATE TABLE test.table",
            "test",
            "CREATE TABLE",
            "table"),
        Arguments.of(
            "jdbc:testdb://localhost",
            "CREATE TABLE table",
            "CREATE TABLE table",
            "CREATE TABLE table",
            null,
            "CREATE TABLE",
            "table"));
  }

  @ParameterizedTest
  @MethodSource("spanNameStream")
  void testProduceProperSpanName(
      String url,
      String query,
      String sanitizedQuery,
      String spanName,
      String databaseName,
      String operation,
      String table)
      throws SQLException {
    Driver driver = new TestDriver();
    Connection connection = driver.connect(url, null);
    cleanup.deferCleanup(connection);

    testing.runWithSpan(
        "parent",
        () -> {
          Statement statement = connection.createStatement();
          statement.executeQuery(query);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "other_sql"),
                            equalTo(maybeStable(DB_NAME), databaseName),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "testdb://localhost"),
                            equalTo(maybeStable(DB_STATEMENT), sanitizedQuery),
                            equalTo(maybeStable(DB_OPERATION), operation),
                            equalTo(maybeStable(DB_SQL_TABLE), table),
                            equalTo(SERVER_ADDRESS, "localhost"))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"hikari", "tomcat", "c3p0"})
  void testConnectionCached(String connectionPoolName) throws SQLException {
    String dbType = "hsqldb";
    DataSource ds = createDs(connectionPoolName, dbType, jdbcUrls.get(dbType));
    cleanup.deferCleanup(
        () -> {
          if (ds instanceof Closeable) {
            ((Closeable) ds).close();
          }
        });
    String query = "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS";
    int numQueries = 5;
    int[] res = new int[numQueries];

    for (int i = 0; i < numQueries; ++i) {
      try (Connection connection = ds.getConnection();
          PreparedStatement statement = connection.prepareStatement(query)) {
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
          res[i] = rs.getInt(1);
        } else {
          res[i] = 0;
        }
      }
    }

    for (int i = 0; i < numQueries; ++i) {
      assertThat(res[i]).isEqualTo(3);
    }

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    Consumer<TraceAssert> traceAssertConsumer =
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT INFORMATION_SCHEMA.SYSTEM_USERS")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "SA"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "SELECT ? FROM INFORMATION_SCHEMA.SYSTEM_USERS"),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "INFORMATION_SCHEMA.SYSTEM_USERS")));
    for (int i = 0; i < numQueries; i++) {
      assertions.add(traceAssertConsumer);
    }

    testing.waitAndAssertTraces(assertions);
  }

  @FunctionalInterface
  public interface ThrowingBiConsumer<T, U> {
    void accept(T t, U u) throws Exception;
  }

  static Stream<Arguments> recursiveStatementsStream() {
    return Stream.of(
        Arguments.of(
            "getMetaData() uses Statement, test Statement",
            false,
            (ThrowingBiConsumer<Connection, String>)
                (con, query) -> con.createStatement().executeQuery(query)),
        Arguments.of(
            "getMetaData() uses PreparedStatement, test Statement",
            true,
            (ThrowingBiConsumer<Connection, String>)
                (con, query) -> con.createStatement().executeQuery(query)),
        Arguments.of(
            "getMetaData() uses Statement, test PreparedStatement",
            false,
            (ThrowingBiConsumer<Connection, String>)
                (con, query) -> con.prepareStatement(query).executeQuery()),
        Arguments.of(
            "getMetaData() uses PreparedStatement, test PreparedStatement",
            true,
            (ThrowingBiConsumer<Connection, String>)
                (con, query) -> con.prepareStatement(query).executeQuery()));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2644
  @ParameterizedTest
  @MethodSource("recursiveStatementsStream")
  void testHandleRecursiveStatements(
      String desc,
      boolean usePreparedStatementInConnection,
      ThrowingBiConsumer<Connection, String> executeQueryFunction)
      throws Exception {
    DbCallingConnection connection = new DbCallingConnection(usePreparedStatementInConnection);
    connection.setUrl("jdbc:testdb://localhost");

    testing.runWithSpan(
        "parent",
        () -> {
          executeQueryFunction.accept(connection, "SELECT * FROM table");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT table")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "other_sql"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "testdb://localhost"),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT * FROM table"),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "table"),
                            equalTo(SERVER_ADDRESS, "localhost"))));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6015
  @DisplayName("test proxy statement")
  @Test
  void testProxyStatement() throws Exception {
    Connection connection = new org.h2.Driver().connect(jdbcUrls.get("h2"), null);
    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    cleanup.deferCleanup(connection);

    Statement proxyStatement = ProxyStatementFactory.proxyStatement(statement);
    ResultSet resultSet =
        testing.runWithSpan("parent", () -> proxyStatement.executeQuery("SELECT 3"));

    resultSet.next();
    assertThat(resultSet.getInt(1)).isEqualTo(3);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT " + dbNameLower)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9359
  @DisplayName("test proxy prepared statement")
  @Test
  void testProxyPreparedStatement() throws SQLException {
    Connection connection = new org.h2.Driver().connect(jdbcUrls.get("h2"), null);
    PreparedStatement statement = connection.prepareStatement("SELECT 3");
    cleanup.deferCleanup(statement);
    cleanup.deferCleanup(connection);

    PreparedStatement proxyStatement = ProxyStatementFactory.proxyPreparedStatement(statement);
    ResultSet resultSet = testing.runWithSpan("parent", () -> proxyStatement.executeQuery());

    resultSet.next();
    assertThat(resultSet.getInt(1)).isEqualTo(3);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT " + dbNameLower)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  static Stream<Arguments> batchStream() throws SQLException {
    return Stream.of(
        Arguments.of("h2", new org.h2.Driver().connect(jdbcUrls.get("h2"), null), null, "h2:mem:"),
        Arguments.of(
            "derby",
            new EmbeddedDriver().connect(jdbcUrls.get("derby"), null),
            "APP",
            "derby:memory:"),
        Arguments.of(
            "hsqldb", new JDBCDriver().connect(jdbcUrls.get("hsqldb"), null), "SA", "hsqldb:mem:"));
  }

  @ParameterizedTest
  @MethodSource("batchStream")
  void testBatch(String system, Connection connection, String username, String url)
      throws SQLException {
    String tableName = "simple_batch_test";
    Statement createTable = connection.createStatement();
    createTable.execute("CREATE TABLE " + tableName + " (id INTEGER not NULL, PRIMARY KEY ( id ))");
    cleanup.deferCleanup(createTable);

    testing.waitForTraces(1);
    testing.clearData();

    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    statement.addBatch("INSERT INTO non_existent_table VALUES(1)");
    statement.clearBatch();
    statement.addBatch("INSERT INTO " + tableName + " VALUES(1)");
    statement.addBatch("INSERT INTO " + tableName + " VALUES(2)");
    testing.runWithSpan(
        "parent", () -> assertThat(statement.executeBatch()).isEqualTo(new int[] {1, 1}));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "BATCH INSERT jdbcunittest." + tableName
                                : "jdbcunittest")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? "INSERT INTO " + tableName + " VALUES(?)"
                                    : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "BATCH INSERT" : null),
                            equalTo(
                                maybeStable(DB_SQL_TABLE),
                                emitStableDatabaseSemconv() ? tableName : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));
  }

  @ParameterizedTest
  @MethodSource("batchStream")
  void testMultiBatch(String system, Connection connection, String username, String url)
      throws SQLException {
    String tableName1 = "multi_batch_test_1";
    String tableName2 = "multi_batch_test_2";
    Statement createTable1 = connection.createStatement();
    createTable1.execute(
        "CREATE TABLE " + tableName1 + " (id INTEGER not NULL, PRIMARY KEY ( id ))");
    cleanup.deferCleanup(createTable1);
    Statement createTable2 = connection.createStatement();
    createTable2.execute(
        "CREATE TABLE " + tableName2 + " (id INTEGER not NULL, PRIMARY KEY ( id ))");
    cleanup.deferCleanup(createTable1);

    testing.waitForTraces(2);
    testing.clearData();

    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    statement.addBatch("INSERT INTO " + tableName1 + " VALUES(1)");
    statement.addBatch("INSERT INTO " + tableName2 + " VALUES(2)");
    testing.runWithSpan(
        "parent", () -> assertThat(statement.executeBatch()).isEqualTo(new int[] {1, 1}));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "BATCH INSERT jdbcunittest"
                                : "jdbcunittest")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                emitStableDatabaseSemconv()
                                    ? "INSERT INTO "
                                        + tableName1
                                        + " VALUES(?); INSERT INTO multi_batch_test_2 VALUES(?)"
                                    : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "BATCH INSERT" : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));
  }

  @ParameterizedTest
  @MethodSource("batchStream")
  void testSingleItemBatch(String system, Connection connection, String username, String url)
      throws SQLException {
    String tableName = "single_item_batch_test";
    Statement createTable = connection.createStatement();
    createTable.execute("CREATE TABLE " + tableName + " (id INTEGER not NULL, PRIMARY KEY ( id ))");
    cleanup.deferCleanup(createTable);

    testing.waitForTraces(1);
    testing.clearData();

    Statement statement = connection.createStatement();
    cleanup.deferCleanup(statement);
    statement.addBatch("INSERT INTO " + tableName + " VALUES(1)");
    testing.runWithSpan(
        "parent", () -> assertThat(statement.executeBatch()).isEqualTo(new int[] {1}));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("INSERT jdbcunittest." + tableName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "INSERT INTO " + tableName + " VALUES(?)"),
                            equalTo(maybeStable(DB_OPERATION), "INSERT"),
                            equalTo(maybeStable(DB_SQL_TABLE), tableName))));
  }

  @ParameterizedTest
  @MethodSource("batchStream")
  void testPreparedBatch(String system, Connection connection, String username, String url)
      throws SQLException {
    String tableName = "prepared_batch_test";
    Statement createTable = connection.createStatement();
    createTable.execute("CREATE TABLE " + tableName + " (id INTEGER not NULL, PRIMARY KEY ( id ))");
    cleanup.deferCleanup(createTable);

    testing.waitForTraces(1);
    testing.clearData();

    PreparedStatement statement =
        connection.prepareStatement("INSERT INTO " + tableName + " VALUES(?)");
    cleanup.deferCleanup(statement);
    statement.setInt(1, 1);
    statement.addBatch();
    statement.clearBatch();
    statement.setInt(1, 1);
    statement.addBatch();
    statement.setInt(1, 2);
    statement.addBatch();
    testing.runWithSpan(
        "parent", () -> assertThat(statement.executeBatch()).isEqualTo(new int[] {1, 1}));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "BATCH INSERT jdbcunittest." + tableName
                                : "INSERT jdbcunittest." + tableName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(system)),
                            equalTo(maybeStable(DB_NAME), dbNameLower),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : username),
                            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : url),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "INSERT INTO " + tableName + " VALUES(?)"),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "BATCH INSERT" : "INSERT"),
                            equalTo(maybeStable(DB_SQL_TABLE), tableName),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? 2L : null))));
  }
}

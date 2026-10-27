/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.R2dbcSqlAttributesGetter;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.R2dbcBadGrammarException;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class R2dbcSqlAttributesGetterTest {

  private final R2dbcSqlAttributesGetter getter = new R2dbcSqlAttributesGetter();

  @ParameterizedTest
  @MethodSource("responseStatuses")
  void responseStatus(Throwable error, String expectedErrorType, String expectedStatus) {
    DbExecution request =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db"));

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo(expectedStatus);
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> responseStatuses() {
    return Stream.of(
        argumentSet(
            "SQLSTATE takes precedence for PostgreSQL",
            new R2dbcBadGrammarException("test", "42601", 42),
            "42601",
            "42601"),
        argumentSet(
            "no SQLSTATE",
            new R2dbcBadGrammarException("test"),
            R2dbcBadGrammarException.class.getName(),
            null),
        argumentSet(
            "non-database failure",
            new IllegalStateException("test"),
            IllegalStateException.class.getName(),
            null),
        argumentSet("success", null, null, null));
  }

  @Test
  void capturesPostgresqlClosedConnectionCodeWithoutChangingErrorType()
      throws ReflectiveOperationException {
    DbExecution request =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db"));
    Throwable error = postgresqlConnectionClosedException();

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("08006");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("08006");
  }

  @Test
  void keepsPostgresqlServerConnectionFailureCode() throws ReflectiveOperationException {
    DbExecution request =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db"));
    Throwable error = postgresqlServerConnectionFailure();

    assertThat(getter.getErrorType(request, null, error)).isEqualTo("08006");
    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("08006");
  }

  @ParameterizedTest
  @ValueSource(strings = {"mysql", "mariadb", "oracle", "mssql", "db2"})
  void prefersVendorCodeWithoutChangingErrorType(String dbSystemName) {
    DbExecution request = request(dbSystemName);
    Throwable error = new R2dbcBadGrammarException("test", "42000", 1064);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("42000");
  }

  @ParameterizedTest
  @ValueSource(strings = {"mysql", "mariadb", "oracle", "mssql", "db2"})
  void usesVendorCodeWhenSqlStateIsUnavailable(String dbSystemName) {
    DbExecution request = request(dbSystemName);
    Throwable error = new R2dbcBadGrammarException("test", null, 1064);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1064");
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(R2dbcBadGrammarException.class.getName());
  }

  @ParameterizedTest
  @MethodSource("otherSqlResponseStatuses")
  void formatsGenericSqlResponseStatus(
      Throwable error, String expectedStatus, String expectedErrorType) {
    DbExecution request = request("unknown");

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo(expectedStatus);
    assertThat(getter.getErrorType(request, null, error)).isEqualTo(expectedErrorType);
  }

  private static Stream<Arguments> otherSqlResponseStatuses() {
    return Stream.of(
        argumentSet(
            "both codes",
            new R2dbcBadGrammarException("test", "42000", 1071),
            "42000/1071",
            "42000"),
        argumentSet(
            "SQLSTATE only", new R2dbcBadGrammarException("test", "42000", 0), "42000", "42000"),
        argumentSet(
            "vendor code only",
            new R2dbcBadGrammarException("test", null, 1071),
            "1071",
            R2dbcBadGrammarException.class.getName()),
        argumentSet(
            "neither code",
            new R2dbcBadGrammarException("test"),
            null,
            R2dbcBadGrammarException.class.getName()));
  }

  @Test
  void keepsVendorCodeWithoutSqlStateForMappedNonPreferredSystem() {
    DbExecution request = request("clickhouse");
    Throwable error = new R2dbcBadGrammarException("test", null, 1071);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("1071");
    assertThat(getter.getErrorType(request, null, error))
        .isEqualTo(R2dbcBadGrammarException.class.getName());
  }

  @Test
  void combinesCodesForUnknownSystem() {
    DbExecution request = request("clickhouse");
    Throwable error = new R2dbcBadGrammarException("test", "42000", 1071);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("42000/1071");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("42000");
  }

  @ParameterizedTest
  @ValueSource(strings = {"mysql", "mariadb", "oracle", "mssql", "db2"})
  void fallsBackToSqlStateWhenVendorCodeIsUnavailable(String dbSystemName) {
    DbExecution request = request(dbSystemName);
    Throwable error = new R2dbcBadGrammarException("test", "42000", 0);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("42000");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("42000");
  }

  @Test
  void capturesSuccessfulSqlStateWithoutChangingErrorType() {
    DbExecution request = request("mysql");
    Throwable error = new R2dbcBadGrammarException("test", "00000", 0);

    assertThat(getter.getDbResponseStatusCode(request, null, error)).isEqualTo("00000");
    assertThat(getter.getErrorType(request, null, error)).isEqualTo("00000");
  }

  private static Throwable postgresqlConnectionClosedException()
      throws ReflectiveOperationException {
    Class<?> exceptionClass =
        Class.forName(
            "io.r2dbc.postgresql.client.ReactorNettyClient$PostgresConnectionClosedException");
    Constructor<?> constructor = exceptionClass.getDeclaredConstructor(String.class);
    constructor.setAccessible(true);
    return (Throwable) constructor.newInstance("Connection closed");
  }

  private static Throwable postgresqlServerConnectionFailure() throws ReflectiveOperationException {
    Class<?> errorDetailsClass = Class.forName("io.r2dbc.postgresql.api.ErrorDetails");
    Object errorDetails =
        errorDetailsClass
            .getMethod("fromCodeAndMessage", String.class, String.class)
            .invoke(null, "08006", "Server connection failure");
    Class<?> exceptionClass =
        Class.forName(
            "io.r2dbc.postgresql.ExceptionFactory$PostgresqlNonTransientResourceException");
    Constructor<?> constructor =
        exceptionClass.getDeclaredConstructor(errorDetailsClass, String.class);
    constructor.setAccessible(true);
    return (Throwable) constructor.newInstance(errorDetails, "SELECT 1");
  }

  @Test
  void rawQueryTextsForSingleQuery() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(dbExecution);

    assertThat(rawQueryTexts).isSameAs(dbExecution.getRawQueryTexts());
    assertThat(rawQueryTexts).containsExactly("INSERT INTO person VALUES(1)");
  }

  @Test
  void rawQueryTextsForBatch() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(1)"))
            .queryInfo(new QueryInfo("INSERT INTO person VALUES(2)"))
            .batchSize(2)
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:postgresql://localhost/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(dbExecution);

    assertThat(rawQueryTexts).isSameAs(dbExecution.getRawQueryTexts());
    assertThat(rawQueryTexts)
        .containsExactly("INSERT INTO person VALUES(1)", "INSERT INTO person VALUES(2)");
    assertThat(getter.getDbOperationBatchSize(dbExecution)).isEqualTo(2);
  }

  @ParameterizedTest
  @MethodSource("dialects")
  void sqlDialect(String connectionFactoryUrl, SqlDialect expectedDialect) {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT * FROM \"customers\""))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    DbExecution dbExecution =
        new DbExecution(queryExecutionInfo, ConnectionFactoryOptions.parse(connectionFactoryUrl));

    assertThat(getter.getSqlDialect(dbExecution)).isEqualTo(expectedDialect);
  }

  private static Stream<Arguments> dialects() {
    return Stream.of(
        argumentSet("PostgreSQL", "r2dbc:postgresql://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("Oracle", "r2dbc:oracle://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("DB2", "r2dbc:db2://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("ClickHouse", "r2dbc:clickhouse://localhost/db", DOUBLE_QUOTES_ARE_IDENTIFIERS),
        argumentSet("MySQL", "r2dbc:mysql://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("SQL Server", "r2dbc:mssql://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS),
        argumentSet("unknown", "r2dbc:unknown://localhost/db", DOUBLE_QUOTES_ARE_STRING_LITERALS));
  }

  @Test
  void multiHostUrlKeepsAddressAndHasNoPort() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.parse("r2dbc:mariadb:sequential://host1:3306,host2:3307/db"));

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1:3306,host2:3307");
    assertThat(getter.getServerPort(dbExecution)).isNull();
  }

  @Test
  void multiHostOptionsOmitTheKnownDefaultPort() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "mariadb")
                .option(ConnectionFactoryOptions.HOST, "host1,host2")
                .option(ConnectionFactoryOptions.PORT, 3306)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1,host2");
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  @Test
  void multiHostOptionsInlineASharedNonDefaultPort() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "mariadb")
                .option(ConnectionFactoryOptions.HOST, "host1,host2")
                .option(ConnectionFactoryOptions.PORT, 3307)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1:3307,host2:3307");
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  @Test
  void multiHostOptionsRemoveUserInfoFromTheConfiguredTarget() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "user:secret@host1,host2")
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1,host2");
  }

  @Test
  void malformedMultiHostOptionsOmitTheConfiguredTarget() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "host1:invalid,host2")
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo(null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[host1]:5432", "[host1]:5432,host2"})
  void bracketedNonIpv6OptionsOmitTheConfiguredTarget(String host) {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, host)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo(null);
    assertThat(getter.getServerPort(dbExecution)).isNull();
  }

  @Test
  void singleHostOptionsRemoveUserInfoFromTheConfiguredTarget() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "user:secret@host1")
                .option(ConnectionFactoryOptions.PORT, 5432)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1");
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  @Test
  void malformedSingleHostOptionsOmitTheConfiguredTarget() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "host1:invalid")
                .option(ConnectionFactoryOptions.PORT, 5432)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo(null);
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  @Test
  void singleHostOmitsKnownDefaultPort() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.parse("r2dbc:postgresql://host1:5432/db"));

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("host1");
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  @Test
  void unixDomainSocketOmitsPortFromConfiguredTarget() {
    DbExecution dbExecution =
        new DbExecution(
            queryExecutionInfo(),
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, "/var/run/postgresql/.s.PGSQL.5432")
                .option(ConnectionFactoryOptions.PORT, 5432)
                .build());

    assertThat(getter.getServerAddress(dbExecution)).isEqualTo("/var/run/postgresql/.s.PGSQL.5432");
    assertThat(getter.getServerPort(dbExecution)).isEqualTo(null);
  }

  private static QueryExecutionInfo queryExecutionInfo() {
    return MockQueryExecutionInfo.builder()
        .queryInfo(new QueryInfo("SELECT 1"))
        .connectionInfo(MockConnectionInfo.builder().build())
        .build();
  }

  private static DbExecution request(String dbSystemName) {
    return new DbExecution(
        queryExecutionInfo(),
        ConnectionFactoryOptions.parse("r2dbc:" + dbSystemName + "://localhost/db"));
  }
}

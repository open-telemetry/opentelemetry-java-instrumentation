/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenTelemetryDataSourceTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("databaseSystems")
  void shouldCaptureCanonicalDatabaseSystemName(String url, String dbSystemName)
      throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setDataSourceInstrumenterEnabled(true)
            .build();
    DataSource dataSource = telemetry.wrap(new TestDataSource(url));

    Connection connection = testing.runWithSpan("parent", () -> dataSource.getConnection());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("TestDataSource.getConnection")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                TestDataSource.class.getName() + ".getConnection"),
                            equalTo(DB_SYSTEM_NAME, dbSystemName),
                            equalTo(DB_NAMESPACE, "dbname"))));
    assertThat(((OpenTelemetryConnection) connection).getDbInfo().getDbSystemName())
        .isEqualTo(dbSystemName);
  }

  private static Stream<Arguments> databaseSystems() {
    return Stream.of(
        argumentSet("H2", "jdbc:h2:mem:dbname", "h2database"),
        argumentSet("Oracle", "jdbc:oracle:thin:@//db.host:1521/dbname", "oracle.db"),
        argumentSet("DB2", "jdbc:db2://db.host:50000/dbname", "ibm.db2"),
        argumentSet(
            "SQL Server",
            "jdbc:sqlserver://db.host:1433;databaseName=dbname",
            "microsoft.sql_server"));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("getConnectionMethodsArguments")
  void shouldEmitGetConnectionSpans(GetConnectionFunction getConnection) throws SQLException {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setDataSourceInstrumenterEnabled(true)
            .build();
    DataSource dataSource = telemetry.wrap(new TestDataSource());

    Connection connection = testing.runWithSpan("parent", () -> getConnection.call(dataSource));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("TestDataSource.getConnection")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CODE_FUNCTION_NAME,
                                TestDataSource.class.getName() + ".getConnection"),
                            equalTo(DB_SYSTEM_NAME, POSTGRESQL),
                            equalTo(DB_NAMESPACE, "dbname"),
                            equalTo(DB_CONNECTION_STRING, null))));

    assertThat(connection).isInstanceOf(OpenTelemetryConnection.class);
    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();
    assertDbInfo(dbInfo);
  }

  @ParameterizedTest
  @MethodSource("getConnectionMethodsArguments")
  void shouldNotEmitGetConnectionSpansWithoutParentSpan(GetConnectionFunction getConnection)
      throws SQLException {
    JdbcTelemetry telemetry = JdbcTelemetry.create(testing.getOpenTelemetry());
    DataSource dataSource = telemetry.wrap(new TestDataSource());

    Connection connection = getConnection.call(dataSource);

    assertThat(testing.waitForTraces(0)).isEmpty();

    assertThat(connection).isInstanceOf(OpenTelemetryConnection.class);
    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();
    assertDbInfo(dbInfo);
  }

  private static Stream<Arguments> getConnectionMethodsArguments() {
    GetConnectionFunction getConnection = DataSource::getConnection;
    GetConnectionFunction getConnectionWithUserAndPass = ds -> ds.getConnection(null, null);
    return Stream.of(arguments(getConnection), arguments(getConnectionWithUserAndPass));
  }

  @FunctionalInterface
  interface GetConnectionFunction {

    Connection call(DataSource dataSource) throws SQLException;
  }

  private static void assertDbInfo(DbInfo dbInfo) {
    assertThat(dbInfo.getDbSystemName()).isEqualTo("postgresql");
    assertThat(dbInfo.getDbNamespace()).isEqualTo("dbname");
    assertThat(dbInfo.getConfiguredServerTarget().getAddress()).isEqualTo("127.0.0.1");
    assertThat(dbInfo.getConfiguredServerTarget().getPort()).isNull();
  }
}

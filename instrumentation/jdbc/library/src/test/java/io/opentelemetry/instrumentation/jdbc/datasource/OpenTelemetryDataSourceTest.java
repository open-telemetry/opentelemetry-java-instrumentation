/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenTelemetryDataSourceTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("getConnectionMethodsArguments")
  void shouldEmitGetConnectionSpans(GetConnectionFunction getConnection) throws SQLException {
    JdbcTelemetry telemetry = JdbcTelemetry.create(testing.getOpenTelemetry());
    DataSource dataSource = telemetry.wrap(new TestDataSource());

    Connection connection = testing.runWithSpan("parent", () -> getConnection.call(dataSource));

    List<AttributeAssertion> assertions =
        SemconvCodeStabilityUtil.codeFunctionAssertions(TestDataSource.class, "getConnection");
    assertions.add(equalTo(maybeStable(DB_SYSTEM), "postgresql"));
    assertions.add(equalTo(maybeStable(DB_NAME), "dbname"));
    assertions.add(
        equalTo(
            DB_CONNECTION_STRING,
            emitStableDatabaseSemconv() ? null : "postgresql://127.0.0.1:5432"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("TestDataSource.getConnection")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(assertions)));

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
    assertThat(dbInfo.getSystem()).isEqualTo("postgresql");
    assertNull(dbInfo.getSubtype());
    assertThat(dbInfo.getShortUrl()).isEqualTo("postgresql://127.0.0.1:5432");
    assertNull(dbInfo.getUser());
    assertNull(dbInfo.getName());
    assertThat(dbInfo.getDb()).isEqualTo("dbname");
    assertThat(dbInfo.getHost()).isEqualTo("127.0.0.1");
    assertThat(dbInfo.getPort()).isEqualTo(5432);
  }
}

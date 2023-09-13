/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class OpenTelemetryDataSourceTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ArgumentsSource(GetConnectionMethods.class)
  void shouldEmitGetConnectionSpans(GetConnectionFunction getConnection) throws SQLException {
    OpenTelemetryDataSource dataSource =
        new OpenTelemetryDataSource(new TestDataSource(), testing.getOpenTelemetry());

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
                                SemanticAttributes.CODE_NAMESPACE, TestDataSource.class.getName()),
                            equalTo(SemanticAttributes.CODE_FUNCTION, "getConnection"),
                            equalTo(SemanticAttributes.DB_SYSTEM, "postgresql"),
                            equalTo(SemanticAttributes.DB_NAME, "dbname"),
                            equalTo(
                                SemanticAttributes.DB_CONNECTION_STRING,
                                "postgresql://127.0.0.1:5432"))));

    assertThat(connection).isExactlyInstanceOf(OpenTelemetryConnection.class);
    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();
    assertDbInfo(dbInfo);
  }

  @ParameterizedTest
  @ArgumentsSource(GetConnectionMethods.class)
  void shouldNotEmitGetConnectionSpansWithoutParentSpan(GetConnectionFunction getConnection)
      throws SQLException {
    OpenTelemetryDataSource dataSource =
        new OpenTelemetryDataSource(new TestDataSource(), testing.getOpenTelemetry());

    Connection connection = getConnection.call(dataSource);

    assertThat(testing.waitForTraces(0)).isEmpty();

    assertThat(connection).isExactlyInstanceOf(OpenTelemetryConnection.class);
    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();
    assertDbInfo(dbInfo);
  }

  static class GetConnectionMethods implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
      GetConnectionFunction getConnection = DataSource::getConnection;
      GetConnectionFunction getConnectionWithUserAndPass = ds -> ds.getConnection(null, null);
      return Stream.of(arguments(getConnection), arguments(getConnectionWithUserAndPass));
    }
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

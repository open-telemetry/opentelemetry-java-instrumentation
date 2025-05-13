/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenTelemetryConnectionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testVerifyCreateStatement(boolean sqlCommenterEnabled) throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    List<String> executedSql = new ArrayList<>();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(executedSql::add), dbInfo, instrumenter, sqlCommenterEnabled);
    String query = "SELECT * FROM users";
    Statement statement = connection.createStatement();

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              assertThat(statement.execute(query)).isTrue();
              return Span.current().getSpanContext();
            });

    assertExecutedSql(executedSql, query, sqlCommenterEnabled, spanContext);
    jdbcTraceAssertion(dbInfo, query);

    statement.close();
    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyCreateStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter, false);

    assertThat(connection.createStatement()).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.createStatement()).instrumenter)
        .isEqualTo(instrumenter);

    connection.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testVerifyPrepareStatement(boolean sqlCommenterEnabled) throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    List<String> executedSql = new ArrayList<>();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(executedSql::add), dbInfo, instrumenter, sqlCommenterEnabled);
    String query = "SELECT * FROM users";

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareStatement(query);
              assertThat(statement.execute()).isTrue();
              ResultSet resultSet = statement.getResultSet();
              assertThat(resultSet).isInstanceOf(OpenTelemetryResultSet.class);
              assertThat(resultSet.getStatement()).isEqualTo(statement);
              statement.close();
              return Span.current().getSpanContext();
            });

    assertExecutedSql(executedSql, query, sqlCommenterEnabled, spanContext);
    jdbcTraceAssertion(dbInfo, query);

    connection.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testVerifyPrepareStatementUpdate(boolean sqlCommenterEnabled) throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    List<String> executedSql = new ArrayList<>();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(executedSql::add), dbInfo, instrumenter, sqlCommenterEnabled);
    String query = "UPDATE users SET name = name";

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareStatement(query);
              statement.executeUpdate();
              assertThat(statement.getResultSet()).isNull();
              statement.close();
              return Span.current().getSpanContext();
            });

    assertExecutedSql(executedSql, query, sqlCommenterEnabled, spanContext);
    jdbcTraceAssertion(dbInfo, query, "UPDATE");

    connection.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testVerifyPrepareStatementQuery(boolean sqlCommenterEnabled) throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    List<String> executedSql = new ArrayList<>();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(executedSql::add), dbInfo, instrumenter, sqlCommenterEnabled);
    String query = "SELECT * FROM users";

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareStatement(query);
              ResultSet resultSet = statement.executeQuery();
              assertThat(resultSet).isInstanceOf(OpenTelemetryResultSet.class);
              assertThat(resultSet.getStatement()).isEqualTo(statement);
              statement.close();
              return Span.current().getSpanContext();
            });

    assertExecutedSql(executedSql, query, sqlCommenterEnabled, spanContext);
    jdbcTraceAssertion(dbInfo, query);

    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter, false);
    String query = "SELECT * FROM users";

    assertThat(connection.prepareStatement(query))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new int[] {0}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new String[] {"id"}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(
            ((OpenTelemetryStatement<Statement>) connection.prepareStatement(query)).instrumenter)
        .isEqualTo(instrumenter);

    connection.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testVerifyPrepareCall(boolean sqlCommenterEnabled) throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    List<String> executedSql = new ArrayList<>();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(executedSql::add), dbInfo, instrumenter, sqlCommenterEnabled);
    String query = "SELECT * FROM users";

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareCall(query);
              assertThat(statement.execute()).isTrue();
              statement.close();
              return Span.current().getSpanContext();
            });

    assertExecutedSql(executedSql, query, sqlCommenterEnabled, spanContext);
    jdbcTraceAssertion(dbInfo, query);

    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareCallReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter, false);
    String query = "SELECT * FROM users";

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.prepareCall(query)).instrumenter)
        .isEqualTo(instrumenter);

    connection.close();
  }

  private static DbInfo getDbInfo() {
    return DbInfo.builder()
        .system("my_system")
        .subtype("my_sub_type")
        .shortUrl("my_connection_string")
        .user("my_user")
        .name("my_name")
        .db("my_db")
        .host("my_host")
        .port(1234)
        .build();
  }

  private static void jdbcTraceAssertion(DbInfo dbInfo, String query) {
    jdbcTraceAssertion(dbInfo, query, "SELECT");
  }

  @SuppressWarnings("deprecation") // old semconv
  private static void jdbcTraceAssertion(DbInfo dbInfo, String query, String operation) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(operation + " my_name.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                maybeStableDbSystemName(dbInfo.getSystem())),
                            equalTo(maybeStable(DB_NAME), dbInfo.getName()),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : dbInfo.getUser()),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : dbInfo.getShortUrl()),
                            equalTo(maybeStable(DB_STATEMENT), query),
                            equalTo(maybeStable(DB_OPERATION), operation),
                            equalTo(maybeStable(DB_SQL_TABLE), "users"),
                            equalTo(SERVER_ADDRESS, dbInfo.getHost()),
                            equalTo(SERVER_PORT, dbInfo.getPort()))));
  }

  private static void assertExecutedSql(
      List<String> executedSql,
      String query,
      boolean sqlCommenterEnabled,
      SpanContext spanContext) {
    assertThat(executedSql).hasSize(1);
    if (sqlCommenterEnabled) {
      assertThat(executedSql.get(0))
          .contains(query)
          .contains("traceparent")
          .contains(spanContext.getTraceId())
          .contains(spanContext.getSpanId());
    } else {
      assertThat(executedSql.get(0)).isEqualTo(query);
    }
  }
}

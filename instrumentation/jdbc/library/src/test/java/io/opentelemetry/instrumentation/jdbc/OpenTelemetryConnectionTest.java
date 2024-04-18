/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryCallableStatement;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryPreparedStatement;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryStatement;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryConnectionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void testVerifyCreateStatement() throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), dbInfo, instrumenter);
    String query = "SELECT * FROM users";
    Statement statement = connection.createStatement();

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute(query)).isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT my_name.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, dbInfo.getSystem()),
                            equalTo(DbIncubatingAttributes.DB_NAME, dbInfo.getName()),
                            equalTo(DbIncubatingAttributes.DB_USER, dbInfo.getUser()),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, query),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
                            equalTo(DbIncubatingAttributes.DB_SQL_TABLE, "users"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, dbInfo.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, dbInfo.getPort()))));

    statement.close();
    connection.close();
  }

  @Test
  void testVerifyCreateStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter);

    assertThat(connection.createStatement()).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0, 0)).isInstanceOf(OpenTelemetryStatement.class);

    Field instrumenterField = OpenTelemetryStatement.class.getDeclaredField("instrumenter");
    instrumenterField.setAccessible(true);

    assertThat(instrumenterField.get(connection.createStatement())).isEqualTo(instrumenter);

    connection.close();
  }

  @Test
  void testVerifyPrepareStatement() throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), dbInfo, instrumenter);
    String query = "SELECT * FROM users";
    PreparedStatement statement = connection.prepareStatement(query);

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute()).isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT my_name.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, dbInfo.getSystem()),
                            equalTo(DbIncubatingAttributes.DB_NAME, dbInfo.getName()),
                            equalTo(DbIncubatingAttributes.DB_USER, dbInfo.getUser()),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, query),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
                            equalTo(DbIncubatingAttributes.DB_SQL_TABLE, "users"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, dbInfo.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, dbInfo.getPort()))));

    statement.close();
    connection.close();
  }

  @Test
  void testVerifyPrepareStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter);
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

    Field instrumenterField = OpenTelemetryStatement.class.getDeclaredField("instrumenter");
    instrumenterField.setAccessible(true);

    assertThat(instrumenterField.get(connection.prepareStatement(query))).isEqualTo(instrumenter);

    connection.close();
  }

  @Test
  void testVerifyPrepareCall() throws SQLException {
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry());
    DbInfo dbInfo = getDbInfo();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), dbInfo, instrumenter);
    String query = "SELECT * FROM users";
    PreparedStatement statement = connection.prepareCall(query);

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute()).isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT my_name.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, dbInfo.getSystem()),
                            equalTo(DbIncubatingAttributes.DB_NAME, dbInfo.getName()),
                            equalTo(DbIncubatingAttributes.DB_USER, dbInfo.getUser()),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, query),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SELECT"),
                            equalTo(DbIncubatingAttributes.DB_SQL_TABLE, "users"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, dbInfo.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, dbInfo.getPort()))));

    statement.close();
    connection.close();
  }

  @Test
  void testVerifyPrepareCallReturnsOtelWrapper() throws Exception {
    OpenTelemetry ot = OpenTelemetry.propagating(ContextPropagators.noop());
    Instrumenter<DbRequest, Void> instrumenter = createStatementInstrumenter(ot);
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT, instrumenter);
    String query = "SELECT * FROM users";

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);

    Field instrumenterField = OpenTelemetryStatement.class.getDeclaredField("instrumenter");
    instrumenterField.setAccessible(true);

    assertThat(instrumenterField.get(connection.prepareStatement(query))).isEqualTo(instrumenter);

    connection.close();
  }

  private static DbInfo getDbInfo() {
    return DbInfo.builder()
        .system("my_system")
        .subtype("my_sub_type")
        .user("my_user")
        .name("my_name")
        .db("my_db")
        .host("my_host")
        .port(1234)
        .build();
  }
}

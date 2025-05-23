/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createTransactionInstrumenter;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_PARAMETER;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryConnectionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void testVerifyCreateStatement() throws SQLException {
    OpenTelemetryConnection connection = getConnection();
    String query = "SELECT * FROM users";
    Statement statement = connection.createStatement();

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute(query)).isTrue();
        });

    jdbcTraceAssertion(connection.getDbInfo(), query);

    statement.close();
    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyCreateStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

    assertThat(connection.createStatement()).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.createStatement()).instrumenter)
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  @Test
  void testVerifyPrepareStatement() throws SQLException {
    OpenTelemetryConnection connection = getConnection();
    String query = "SELECT * FROM users";
    PreparedStatement statement = connection.prepareStatement(query);

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute()).isTrue();
          ResultSet resultSet = statement.getResultSet();
          assertThat(resultSet).isInstanceOf(OpenTelemetryResultSet.class);
          assertThat(resultSet.getStatement()).isEqualTo(statement);
        });

    jdbcTraceAssertion(connection.getDbInfo(), query);

    statement.close();
    connection.close();
  }

  @Test
  void testVerifyPrepareStatementUpdate() throws SQLException {
    OpenTelemetryConnection connection = getConnection();
    String query = "UPDATE users SET name = name";
    PreparedStatement statement = connection.prepareStatement(query);

    testing.runWithSpan(
        "parent",
        () -> {
          statement.executeUpdate();
          assertThat(statement.getResultSet()).isNull();
        });

    jdbcTraceAssertion(connection.getDbInfo(), query, "UPDATE");

    statement.close();
    connection.close();
  }

  @Test
  void testVerifyPrepareStatementQuery() throws SQLException {
    OpenTelemetryConnection connection = getConnection();
    String query = "SELECT * FROM users";
    PreparedStatement statement = connection.prepareStatement(query);

    testing.runWithSpan(
        "parent",
        () -> {
          ResultSet resultSet = statement.executeQuery();
          assertThat(resultSet).isInstanceOf(OpenTelemetryResultSet.class);
          assertThat(resultSet.getStatement()).isEqualTo(statement);
        });

    jdbcTraceAssertion(connection.getDbInfo(), query);

    statement.close();
    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareStatementReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

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
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  @Test
  void testVerifyPrepareCall() throws SQLException {
    OpenTelemetryConnection connection = getConnection();
    String query = "SELECT * FROM users";
    PreparedStatement statement = connection.prepareCall(query);

    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(statement.execute()).isTrue();
        });

    jdbcTraceAssertion(connection.getDbInfo(), query);

    statement.close();
    connection.close();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareCallReturnsOtelWrapper() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);

    String query = "SELECT * FROM users";

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.prepareCall(query)).instrumenter)
        .isEqualTo(connection.statementInstrumenter);

    connection.close();
  }

  @Test
  void testVerifyCommit() throws Exception {
    OpenTelemetryConnection connection = getConnection();

    testing.runWithSpan("parent", connection::commit);
    transactionTraceAssertion(connection.getDbInfo(), "COMMIT");

    connection.close();
  }

  @Test
  void testVerifyRollback() throws Exception {
    OpenTelemetryConnection connection = getConnection();

    testing.runWithSpan("parent", () -> connection.rollback());
    transactionTraceAssertion(connection.getDbInfo(), "ROLLBACK");

    connection.close();
  }

  // https://github.com/open-telemetry/semantic-conventions/pull/2093
  @SuppressWarnings("deprecation")
  @Test
  void testVerifyPrepareStatementParameters() throws SQLException, MalformedURLException {
    OpenTelemetry openTelemetry = testing.getOpenTelemetry();
    Instrumenter<DbRequest, Void> instrumenter =
        createStatementInstrumenter(testing.getOpenTelemetry(), true);
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        createTransactionInstrumenter(openTelemetry, false);
    DbInfo dbInfo = getDbInfo();
    OpenTelemetryConnection connection =
        new OpenTelemetryConnection(
            new TestConnection(), dbInfo, instrumenter, transactionInstrumenter, true);
    String query = "SELECT * FROM users WHERE id=? AND age=3";
    String sanitized = "SELECT * FROM users WHERE id=? AND age=3";
    PreparedStatement statement = connection.prepareStatement(query);
    // doesn't need to match the number of placeholders in this context
    statement.setBoolean(1, true);
    statement.setShort(2, (short) 1);
    statement.setInt(3, 2);
    statement.setLong(4, 3);
    statement.setFloat(5, 4);
    statement.setDouble(6, 5.5);
    statement.setBigDecimal(7, BigDecimal.valueOf(6));
    statement.setString(8, "S");
    statement.setDate(9, Date.valueOf("2000-01-01"));
    statement.setDate(10, Date.valueOf("2000-01-02"), Calendar.getInstance());
    statement.setTime(11, Time.valueOf("00:00:00"));
    statement.setTime(12, Time.valueOf("00:00:01"), Calendar.getInstance());
    statement.setTimestamp(13, Timestamp.valueOf("2000-01-01 00:00:00"));
    statement.setTimestamp(14, Timestamp.valueOf("2000-01-01 00:00:01"), Calendar.getInstance());
    statement.setURL(15, URI.create("http://localhost:8080").toURL());
    statement.setNString(16, "S");

    testing.runWithSpan(
        "parent",
        () -> {
          ResultSet resultSet = statement.executeQuery();
          assertThat(resultSet).isInstanceOf(OpenTelemetryResultSet.class);
          assertThat(resultSet.getStatement()).isEqualTo(statement);
        });

    jdbcTraceAssertion(
        dbInfo,
        sanitized,
        "SELECT",
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("0"), "true"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("1"), "1"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("2"), "2"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("3"), "3"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("4"), "4.0"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("5"), "5.5"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("6"), "6"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("7"), "S"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("8"), "2000-01-01"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("9"), "2000-01-02"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("10"), "00:00:00"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("11"), "00:00:01"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("12"), "2000-01-01 00:00:00.0"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("13"), "2000-01-01 00:00:01.0"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("14"), "http://localhost:8080"),
        equalTo(DB_QUERY_PARAMETER.getAttributeKey("15"), "S"));

    statement.close();
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
  private static void jdbcTraceAssertion(
      DbInfo dbInfo, String query, String operation, AttributeAssertion... assertions) {
    List<AttributeAssertion> baseAttributeAssertions =
        Arrays.asList(
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName(dbInfo.getSystem())),
            equalTo(maybeStable(DB_NAME), dbInfo.getName()),
            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : dbInfo.getUser()),
            equalTo(
                DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : dbInfo.getShortUrl()),
            equalTo(maybeStable(DB_STATEMENT), query),
            equalTo(maybeStable(DB_OPERATION), operation),
            equalTo(maybeStable(DB_SQL_TABLE), "users"),
            equalTo(SERVER_ADDRESS, dbInfo.getHost()),
            equalTo(SERVER_PORT, dbInfo.getPort()));

    List<AttributeAssertion> additionAttributeAssertions = Arrays.asList(assertions);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(operation + " my_name.users")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            Stream.concat(
                                    baseAttributeAssertions.stream(),
                                    additionAttributeAssertions.stream())
                                .collect(Collectors.toList()))));
  }

  @SuppressWarnings("deprecation") // old semconv
  private static void transactionTraceAssertion(DbInfo dbInfo, String operation) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(operation)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                maybeStableDbSystemName(dbInfo.getSystem())),
                            equalTo(maybeStable(DB_NAME), dbInfo.getName()),
                            equalTo(maybeStable(DB_OPERATION), operation),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : dbInfo.getUser()),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : dbInfo.getShortUrl()),
                            equalTo(SERVER_ADDRESS, dbInfo.getHost()),
                            equalTo(SERVER_PORT, dbInfo.getPort()))));
  }

  private static OpenTelemetryConnection getConnection() {
    return getConnection(testing.getOpenTelemetry());
  }

  private static OpenTelemetryConnection getConnection(OpenTelemetry openTelemetry) {
    Instrumenter<DbRequest, Void> statementInstrumenter =
        createStatementInstrumenter(openTelemetry);
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        createTransactionInstrumenter(openTelemetry, true);
    DbInfo dbInfo = getDbInfo();
    return new OpenTelemetryConnection(
        new TestConnection(), dbInfo, statementInstrumenter, transactionInstrumenter, false);
  }
}

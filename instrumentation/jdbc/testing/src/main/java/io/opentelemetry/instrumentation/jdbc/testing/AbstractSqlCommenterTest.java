/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractSqlCommenterTest {
  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract InstrumentationExtension testing();

  protected Connection createConnection(List<String> executedSql) throws SQLException {
    return new TestConnection(executedSql::add);
  }

  @Test
  void testSqlCommenterStatement() throws SQLException {
    List<String> executedSql = new ArrayList<>();
    Connection connection = createConnection(executedSql);
    Statement statement = connection.createStatement();

    cleanup.deferCleanup(statement);
    cleanup.deferCleanup(connection);

    String query = "SELECT 1";
    testing().runWithSpan("parent", () -> statement.execute(query));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> span.hasName("SELECT dbname").hasParent(trace.getSpan(0))));

    assertThat(executedSql).hasSize(1);
    assertThat(executedSql.get(0)).contains(query).contains("traceparent");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSqlCommenterStatementUpdate(boolean largeUpdate) throws SQLException {
    List<String> executedSql = new ArrayList<>();
    Connection connection = createConnection(executedSql);
    Statement statement = connection.createStatement();

    cleanup.deferCleanup(statement);
    cleanup.deferCleanup(connection);

    String query = "INSERT INTO test VALUES(1)";
    testing()
        .runWithSpan(
            "parent",
            () -> {
              if (largeUpdate) {
                statement.executeLargeUpdate(query);
              } else {
                statement.execute(query);
              }
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> span.hasName("INSERT dbname.test").hasParent(trace.getSpan(0))));

    assertThat(executedSql).hasSize(1);
    assertThat(executedSql.get(0)).contains(query).contains("traceparent");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSqlCommenterStatementBatch(boolean largeUpdate) throws SQLException {
    List<String> executedSql = new ArrayList<>();
    Connection connection = createConnection(executedSql);
    Statement statement = connection.createStatement();

    cleanup.deferCleanup(statement);
    cleanup.deferCleanup(connection);

    testing()
        .runWithSpan(
            "parent",
            () -> {
              statement.addBatch("INSERT INTO test VALUES(1)");
              statement.addBatch("INSERT INTO test VALUES(2)");
              if (largeUpdate) {
                statement.executeLargeBatch();
              } else {
                statement.executeBatch();
              }
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName(
                                SemconvStability.emitStableDatabaseSemconv()
                                    ? "BATCH INSERT dbname.test"
                                    : "dbname")
                            .hasParent(trace.getSpan(0))));

    assertThat(executedSql).hasSize(2);
    assertThat(executedSql.get(0)).contains("INSERT INTO test VALUES(1)").contains("traceparent");
    assertThat(executedSql.get(1)).contains("INSERT INTO test VALUES(2)").contains("traceparent");
  }

  @Test
  void testSqlCommenterPreparedStatement() throws SQLException {
    List<String> executedSql = new ArrayList<>();
    Connection connection = createConnection(executedSql);

    String query = "SELECT 1";
    testing()
        .runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareStatement(query);
              cleanup.deferCleanup(statement);
              cleanup.deferCleanup(connection);

              statement.execute();
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> span.hasName("SELECT dbname").hasParent(trace.getSpan(0))));

    assertThat(executedSql).hasSize(1);
    assertThat(executedSql.get(0)).contains(query).contains("traceparent");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSqlCommenterPreparedStatementUpdate(boolean largeUpdate) throws SQLException {
    List<String> executedSql = new ArrayList<>();
    Connection connection = createConnection(executedSql);

    String query = "INSERT INTO test VALUES(1)";
    testing()
        .runWithSpan(
            "parent",
            () -> {
              PreparedStatement statement = connection.prepareStatement(query);
              cleanup.deferCleanup(statement);
              cleanup.deferCleanup(connection);

              if (largeUpdate) {
                statement.executeLargeUpdate();
              } else {
                statement.executeUpdate();
              }
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> span.hasName("INSERT dbname.test").hasParent(trace.getSpan(0))));

    assertThat(executedSql).hasSize(1);
    assertThat(executedSql.get(0)).contains(query).contains("traceparent");
  }
}

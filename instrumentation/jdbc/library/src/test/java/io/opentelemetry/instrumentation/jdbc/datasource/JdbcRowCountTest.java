/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.jdbc.ConnectionWrapper;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcRowCountTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static Connection rawConnection;

  @BeforeAll
  static void setUp() throws Exception {
    rawConnection = DriverManager.getConnection("jdbc:h2:mem:rowcounttest", "sa", "");
    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50))");
    }
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (rawConnection != null) {
      rawConnection.close();
    }
  }

  private Connection wrapWithRowCount() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry()).setCaptureRowCount(true).build();
    return ConnectionWrapper.wrap(rawConnection, telemetry);
  }

  private Connection wrapWithoutRowCount() throws Exception {
    JdbcTelemetry telemetry = JdbcTelemetry.builder(testing.getOpenTelemetry()).build();
    return ConnectionWrapper.wrap(rawConnection, telemetry);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 50})
  void rowCountForSelectWithStatement(int expectedRows) throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      for (int i = 0; i < expectedRows; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
    testing.clearData();

    Connection connection = wrapWithRowCount();
    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
            int count = 0;
            while (rs.next()) {
              count++;
            }
            assertThat(count).isEqualTo(expectedRows);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("SELECT test_table")
                        .hasAttribute(
                            equalTo(
                                AttributeKey.longKey("db.response.returned_rows"),
                                (long) expectedRows))));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 50})
  void rowCountForSelectWithPreparedStatement(int expectedRows) throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      for (int i = 0; i < expectedRows; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
    testing.clearData();

    Connection connection = wrapWithRowCount();
    testing.runWithSpan(
        "parent",
        () -> {
          try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM test_table");
              ResultSet rs = ps.executeQuery()) {
            int count = 0;
            while (rs.next()) {
              count++;
            }
            assertThat(count).isEqualTo(expectedRows);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("SELECT test_table")
                        .hasAttribute(
                            equalTo(
                                AttributeKey.longKey("db.response.returned_rows"),
                                (long) expectedRows))));
  }

  @Test
  void rowCountOmittedWhenDisabled() throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'row1')");
      stmt.executeUpdate("INSERT INTO test_table VALUES (2, 'row2')");
    }
    testing.clearData();

    Connection connection = wrapWithoutRowCount();
    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
            while (rs.next()) {
              // consume
            }
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("SELECT test_table")
                        .satisfies(
                            s ->
                                assertThat(
                                        s.getAttributes()
                                            .get(AttributeKey.longKey("db.response.returned_rows")))
                                    .isNull())));
  }

  @Test
  void rowCountOmittedWhenExceedsLimit() throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      for (int i = 0; i < 20; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
    testing.clearData();

    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setCaptureRowCount(true)
            .setRowCountLimit(10)
            .build();
    Connection connection = ConnectionWrapper.wrap(rawConnection, telemetry);

    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
            int count = 0;
            while (rs.next()) {
              count++;
            }
            assertThat(count).isEqualTo(20);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("SELECT test_table")
                        .satisfies(
                            s ->
                                assertThat(
                                        s.getAttributes()
                                            .get(AttributeKey.longKey("db.response.returned_rows")))
                                    .isNull())));
  }

  @Test
  void dmlDoesNotCaptureRowCount() throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    try (Statement stmt = rawConnection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'test1')");
      stmt.executeUpdate("INSERT INTO test_table VALUES (2, 'test2')");
    }
    testing.clearData();

    Connection connection = wrapWithRowCount();
    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE test_table SET name = 'updated'");
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("UPDATE test_table")
                        .satisfies(
                            s ->
                                assertThat(
                                        s.getAttributes()
                                            .get(AttributeKey.longKey("db.response.returned_rows")))
                                    .isNull())));
  }
}

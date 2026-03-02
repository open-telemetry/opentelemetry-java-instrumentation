/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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

class JdbcRowCountInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Connection connection;

  @BeforeAll
  static void setUp() throws Exception {
    connection = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50))");
    }
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5, 50, 100})
  void rowCountForSelectWithStatement(int expectedRows) throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      for (int i = 0; i < expectedRows; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
    testing.clearData();

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
  @ValueSource(ints = {0, 1, 5, 50, 100})
  void rowCountForSelectWithPreparedStatement(int expectedRows) throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      for (int i = 0; i < expectedRows; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
    testing.clearData();

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
  void rowCountNotCapturedForDml() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM test_table");
      stmt.executeUpdate("INSERT INTO test_table VALUES (1, 'test1')");
      stmt.executeUpdate("INSERT INTO test_table VALUES (2, 'test2')");
      stmt.executeUpdate("INSERT INTO test_table VALUES (3, 'test3')");
    }
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement()) {
            int updated = stmt.executeUpdate("UPDATE test_table SET name = 'updated'");
            assertThat(updated).isEqualTo(3);
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

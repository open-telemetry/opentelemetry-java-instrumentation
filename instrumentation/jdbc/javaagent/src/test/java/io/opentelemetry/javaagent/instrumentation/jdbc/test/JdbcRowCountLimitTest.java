/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcRowCountLimitTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static Connection connection;

  @BeforeAll
  static void setUp() throws Exception {
    connection = DriverManager.getConnection("jdbc:h2:mem:testLimit", "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY, name VARCHAR(50))");
      for (int i = 0; i < 150; i++) {
        stmt.executeUpdate("INSERT INTO test_table VALUES (" + i + ", 'row" + i + "')");
      }
    }
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (connection != null) {
      connection.close();
    }
  }

  @Test
  void rowCountOmittedWhenExceedsLimit() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Statement stmt = connection.createStatement();
              ResultSet rs = stmt.executeQuery("SELECT * FROM test_table")) {
            int count = 0;
            while (rs.next()) {
              count++;
            }
            // Verify we actually got 150 rows
            assertThat(count).isEqualTo(150);
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
}
